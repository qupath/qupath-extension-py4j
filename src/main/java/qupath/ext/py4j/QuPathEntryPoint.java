/*-
 * Copyright 2022 QuPath developers,  University of Edinburgh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package qupath.ext.py4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import ij.ImagePlus;
import ij.io.FileSaver;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.GuiTools.SnapshotType;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.FeatureCollection;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

/**
 * Entry point for use with a Py4J Gateway.
 * This provides useful methods to work with QuPath from Python.
 * 
 * @author Pete Bankhead
 */
public class QuPathEntryPoint extends QPEx {
	
	public static QuPathGUI getQuPath() {
		return QuPathGUI.getInstance();
	}
	
	public static String getExtensionVersion() {
		return GeneralTools.getPackageVersion(QuPathEntryPoint.class);
	}
	
	public static byte[] snapshot(QuPathGUI qupath) {
		return toTiffBytes(GuiTools.makeSnapshot(qupath, SnapshotType.MAIN_SCENE));
	}

	public static byte[] snapshot(QuPathViewer viewer) {
		return toTiffBytes(GuiTools.makeViewerSnapshot(viewer));
	}

	public static ImageData<BufferedImage> getCurrentImageData() {
		var imageData = QP.getCurrentImageData();
		if (imageData == null)
			imageData = getImageData();
		System.err.println("Returning image: " + imageData);
		return imageData;
	}

	public static ImageData<BufferedImage> getImageData() {
		var qupath = getQuPath();
		return qupath == null ? null : qupath.getImageData();
	}

	public static ImageServer<BufferedImage> getServer() {
		var imageData = getImageData();
		return imageData == null ? null : imageData.getServer();
	}
	
	public static ServerWrapper getWrappedServer() {
		var server = getServer();
		return server == null ? null : wrap(server);
	}
	
//	public PathObjectHierarchy getHierarchy() {
//		var imageData = getImageData();
//		return imageData == null ? null : imageData.getHierarchy();
//	}
	

	public static String getDetectionMeasurementTable(ImageData<?> imageData) {
		if (imageData == null)
			return "";
		return getMeasurementTable(imageData, imageData.getHierarchy().getDetectionObjects());
	}
	
	public static String getAnnotationMeasurementTable(ImageData<?> imageData) {
		if (imageData == null)
			return "";
		return getMeasurementTable(imageData, imageData.getHierarchy().getAnnotationObjects());
	}
	
	public static String getMeasurementTable(ImageData<?> imageData, Collection<? extends PathObject> pathObjects) {
		if (imageData == null || pathObjects == null || pathObjects.isEmpty())
			return "";
		var table = new ObservableMeasurementTableData();
		table.setImageData(imageData, pathObjects);
		return SummaryMeasurementTableCommand.getTableModelString(table, "\t", Collections.emptyList());
	}
		
	/**
	 * Create a {@link PathObject} from a GeoJSON representation.
	 * @param geoJson
	 * @return
	 */
	public static PathObject toPathObject(String geoJson) {
		return GsonTools.getInstance().fromJson(geoJson, PathObject.class);
	}

	/**
	 * Create a {@link ROI} from a GeoJSON representation.
	 * @param geoJson
	 * @return
	 */
	public static ROI toROI(String geoJson) {
		return GsonTools.getInstance().fromJson(geoJson, ROI.class);
	}

	public static List<PathObject> toPathObjects(String geoJson) {
		var gson = GsonTools.getInstance();
		var jsonObject = gson.fromJson(geoJson, JsonElement.class);
		if (jsonObject.isJsonObject() && "FeatureCollection".equals(jsonObject.getAsJsonObject().get("type").getAsString()))
			jsonObject = jsonObject.getAsJsonObject().get("features");
		if (jsonObject.isJsonArray())
			return gson.fromJson(jsonObject,
				new TypeToken<List<PathObject>>() {}.getType()
				);
		else if (jsonObject.getAsJsonObject().size() == 0) {
			return Collections.emptyList();
		} else
			return Collections.singletonList(
					gson.fromJson(jsonObject, PathObject.class)
					);
	}
	
	public static List<ROI> toROIs(String geoJson) {
		return GsonTools.getInstance().fromJson(geoJson, 
				new TypeToken<List<ROI>>() {}.getType()
				);
	}
	
	/**
	 * Convert a collection of PathObjects to a GeoJSON FeatureCollection.
	 * If there is a chance the resulting string will be too long, prefer instead
	 * {@link #toGeoJson(Collection)} to return a list of one string per PathObject instead.
	 * @param pathObjects
	 * @return
	 */
	public static String toFeatureCollection(Collection<? extends PathObject> pathObjects) {
		var collection = FeatureCollection.wrap(pathObjects);
		return GsonTools.getInstance().toJson(collection);
	}
	
	public static List<String> toGeoJson(Collection<? extends PathObject> pathObjects) {
		var gson = GsonTools.getInstance();
		return pathObjects.stream().map(p -> gson.toJson(p)).collect(Collectors.toList());
	}

	public static String toGeoJson(PathObject pathObject) {
		return GsonTools.getInstance().toJson(pathObject);
	}
	
	public static String toGeoJson(ROI roi) {
		return GsonTools.getInstance().toJson(roi);
	}
	

	public static byte[] getTiffStack(ImageServer<BufferedImage> server, double downsample) throws IOException {
		return getTiff(server, downsample, 0, 0, server.getWidth(), server.getHeight());
	}

	public static byte[] getTiffStack(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height) throws IOException {
		return getTiffStack(server, downsample, x, y, width, height, 0, 0);		
	}

	public static byte[] getTiffStack(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
		return getTiffStack(server, RegionRequest.createInstance(server.getPath(), 
							downsample, x, y, width, height, z, t));	
	}
	
	public static byte[] getTiffStack(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		var imp = IJTools.extractHyperstack(server, request);
		return toTiffBytes(imp);
	}
	
	public static byte[] getTiff(ImageServer<BufferedImage> server, double downsample) throws IOException {
		return getTiff(server, downsample, 0, 0, server.getWidth(), server.getHeight());
	}

	public static byte[] getTiff(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height) throws IOException {
		return getTiff(server, downsample, x, y, width, height, 0, 0);		
	}

	public static byte[] getTiff(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
		return getTiff(server, RegionRequest.createInstance(server.getPath(), 
							downsample, x, y, width, height, z, t));		
	}

	public static byte[] getTiff(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		var imp = IJTools.convertToImagePlus(server, request).getImage();
		return toTiffBytes(imp);
	}
	
	
	private static byte[] toTiffBytes(BufferedImage img) {
		
		if (BufferedImageTools.is8bitColorType(img.getType()))
			img = BufferedImageTools.ensureBufferedImageType(img, BufferedImage.TYPE_INT_ARGB);
		
		return toTiffBytes(IJTools.convertToUncalibratedImagePlus("Anything", img));
	}
	
	
	private static byte[] toTiffBytes(ImagePlus imp) {
		return new FileSaver(imp).serialize();
	}
	
	
	public static void printMe(Object obj) {
		System.out.println(Thread.currentThread());
		System.err.println(obj + " -> " + obj.getClass());
	}
	
	
	public static ServerWrapper wrap(ImageServer<BufferedImage> server) {
		return new ServerWrapper(server);
	}
	
	
	static class ServerWrapper {
		
		private ImageServer<BufferedImage> server;
		
		ServerWrapper(ImageServer<BufferedImage> server) {
			this.server = server;
		}
		
		public byte[] readImage(RegionRequest request) throws IOException {
			return getTiff(server, request);
		}
		
		public byte[] readImage(double downsample, int x, int y, int width, int height) throws IOException {
			return readImage(RegionRequest.createInstance(server.getPath(), downsample, x, y, width, height));
		}
		
		public byte[] readImage(double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
			return readImage(RegionRequest.createInstance(server.getPath(), downsample, x, y, width, height, z, t));
		}
		
		public Iterator<?> getTiles() {
			return getTiles(0);
		}
		
		public Iterator<?> getTiles(int level) {
			return new ArrayList<>(server.getTileRequestManager().getTileRequestsForLevel(level))
				.stream()
				.map(t -> {
					try {
						return readImage(t.getRegionRequest());
					} catch (IOException e) {
						return e;
					}
				})
				.iterator();
		}
		
		public ImageServer<BufferedImage> getServer() {
			return server;
		}
		
		public String getMetadataJson() {
			return GsonTools.getInstance(true).toJson(server.getMetadata());
		}
		
		public String getPixelCalibration() {
			return GsonTools.getInstance(true).toJson(server.getPixelCalibration());
		}
		
	}
	
}
