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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import ij.ImagePlus;
import ij.io.FileSaver;
import qupath.imagej.tools.IJTools;
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

/**
 * Entry point for use with a Py4J Gateway.
 * This provides useful methods to work with QuPath from Python.
 * 
 * @author Pete Bankhead
 */
public class QuPathEntryPoint extends QPEx {
	
	public static String getExtensionVersion() {
		return GeneralTools.getPackageVersion(QuPathEntryPoint.class);
	}
	
	public static byte[] snapshot(QuPathGUI qupath) throws IOException {
		return getImageBytes(GuiTools.makeSnapshot(qupath, SnapshotType.MAIN_SCENE), "png");
	}

	public static byte[] snapshot(QuPathViewer viewer) throws IOException {
		return getImageBytes(GuiTools.makeViewerSnapshot(viewer), "png");
	}
	
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
		return getTiffStack(server, downsample, 0, 0, server.getWidth(), server.getHeight());
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
	
	public static byte[] getImageBytes(ImageServer<BufferedImage> server, double downsample, String format) throws IOException {
		return getImageBytes(server, downsample, 0, 0, server.getWidth(), server.getHeight(), format);
	}

	public static byte[] getImageBytes(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, String format) throws IOException {
		return getImageBytes(server, downsample, x, y, width, height, 0, 0, format);		
	}

	public static byte[] getImageBytes(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t, String format) throws IOException {
		var request = RegionRequest.createInstance(server.getPath(), 
				downsample, x, y, width, height, z, t);
		return getImageBytes(server, request, format);		
	}

	public static byte[] getImageBytes(ImageServer<BufferedImage> server, RegionRequest request, String format) throws IOException {
		var fmt = format.toLowerCase();
		if (Set.of("imagej tiff", "imagej tif").contains(fmt)) {
			var imp = IJTools.convertToImagePlus(server, request).getImage();
			return toTiffBytes(imp);
		}
		
		var img = server.readRegion(request);
		return getImageBytes(img, format);
	}
	
		
	
	public static byte[] getImageBytes(BufferedImage img, String format) throws IOException {
		
		var fmt = format.toLowerCase();
		if (Set.of("imagej tiff", "imagej tif").contains(fmt)) {
			var imp = IJTools.convertToUncalibratedImagePlus("Image", img);
			return toTiffBytes(imp);
		}
		
		try (var stream = new ByteArrayOutputStream(Math.min(1024*1024*10, img.getWidth() * img.getHeight() + 1024))) {
			ImageIO.write(img, format, stream);
			var array = stream.toByteArray();
			return array;
		}
		
	}
	
	
	private static byte[] toTiffBytes(ImagePlus imp) {
		return new FileSaver(imp).serialize();
	}
	
	
//	public static ImageBytesServer wrap(ImageServer<BufferedImage> server) {
//		return new ImageBytesServer(server);
//	}
//	
//	
//	static class ImageBytesServer extends AbstractImageServer<byte[]> {
//
//		private String format;
//		private ImageServer<BufferedImage> baseServer;
//		
//		protected ImageBytesServer(ImageServer<BufferedImage> baseServer) {
//			super(byte[].class);
//			this.baseServer = baseServer;
//			if (this.baseServer.isRGB())
//				format = "png";
//			else
//				format = "imagej tiff";
//		}
//
//		@Override
//		public Collection<URI> getURIs() {
//			return baseServer.getURIs();
//		}
//
//		@Override
//		public String getServerType() {
//			return "TIFF bytes server (" + baseServer.getServerType() + ")";
//		}
//
//		@Override
//		public ImageServerMetadata getOriginalMetadata() {
//			return baseServer.getOriginalMetadata();
//		}
//
//		@Override
//		protected ServerBuilder<byte[]> createServerBuilder() {
//			throw new UnsupportedOperationException("Unable to create a ServerBuilder for TiffBytesServer");
//		}
//
//		@Override
//		protected String createID() {
//			return UUID.randomUUID().toString();
//		}
//		
//		@Override
//		public byte[] readRegion(double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
//			return getImageBytes(baseServer, downsample, x, y, width, height, z, t, format);
//		}
//		
//		public Iterator<?> getTiles() {
//			return getTiles(0);
//		}
//		
//		public Iterator<?> getTiles(int level) {
//			return new ArrayList<>(baseServer.getTileRequestManager().getTileRequestsForLevel(level))
//				.stream()
//				.map(t -> {
//					try {
//						return readRegion(t.getRegionRequest());
//					} catch (IOException e) {
//						return e;
//					}
//				})
//				.iterator();
//		}
//		
//		public String getMetadataJson() {
//			return GsonTools.getInstance(true).toJson(baseServer.getMetadata());
//		}
//		
//		public String getPixelCalibrationJson() {
//			return GsonTools.getInstance(true).toJson(baseServer.getPixelCalibration());
//		}
//		
//	}
	
}
