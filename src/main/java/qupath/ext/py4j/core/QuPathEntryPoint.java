package qupath.ext.py4j.core;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import ij.ImagePlus;
import ij.io.FileSaver;
import javafx.application.Platform;
import qupath.fx.utils.FXUtils;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.GuiTools.SnapshotType;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.FeatureCollection;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Entry point for use with a Py4J Gateway.
 * This provides useful methods to work with QuPath from Python.
 */
public class QuPathEntryPoint extends QPEx {

	/**
	 * @return the current version of this extension
	 */
	public static String getExtensionVersion() {
		return GeneralTools.getPackageVersion(QuPathEntryPoint.class);
	}

	/**
	 * Make and return a snapshot (image) showing what is currently displayed in the provided QuPath window.
	 *
	 * @param qupath  the window to snapshot
	 * @return an array of bytes of the image with the PNG format
	 * @throws IOException if an error occurs during writing
	 */
	public static byte[] snapshot(QuPathGUI qupath) throws IOException {
		// If we return the snapshot too quickly, we may not see the result of recent actions
		try {
			Platform.requestNextPulse();
			Thread.sleep(50L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return getImageBytes(GuiTools.makeSnapshot(qupath, SnapshotType.MAIN_SCENE), "png");
	}

	/**
	 * Make and return a snapshot (image) showing what is currently displayed in the provided QuPath viewer.
	 *
	 * @param viewer  the viewer to snapshot
	 * @return an array of bytes of the image with the PNG format
	 * @throws IOException if an error occurs during writing
	 */
	public static byte[] snapshot(QuPathViewer viewer) throws IOException {
		try {
			Platform.requestNextPulse();
			Thread.sleep(50L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return getImageBytes(GuiTools.makeViewerSnapshot(viewer), "png");
	}

	/**
	 * Same as {@link #snapshot(QuPathGUI)}, but encoded with the {@link Base64} scheme.
	 */
	public static String snapshotBase64(QuPathGUI qupath) throws IOException {
		return base64Encode(snapshot(qupath));
	}

	/**
	 * Same as {@link #snapshot(QuPathViewer)}, but encoded with the {@link Base64} scheme.
	 */
	public static String snapshotBase64(QuPathViewer viewer) throws IOException {
		return base64Encode(snapshot(viewer));
	}

	/**
	 * Open the image represented by the specified ProjectImageEntry in the
	 * current QuPath instance.
	 *
	 * @param entry  the image entry to open
	 * @return a boolean indicating if the image was opened
	 */
	public static boolean openInQuPath(ProjectImageEntry<BufferedImage> entry) {
		return FXUtils.callOnApplicationThread(() -> getQuPath().openImageEntry(entry));
	}

	/**
	 * Return the measurement table in text format of all detections
	 * of the provided image.
	 *
	 * @param imageData  the image containing the measurements to retrieve
	 * @return a string representation of the measurement table
	 */
	public static String getDetectionMeasurementTable(ImageData<?> imageData) {
		return imageData == null ? "" : getMeasurementTable(imageData, imageData.getHierarchy().getDetectionObjects());
	}

	/**
	 * Return the measurement table in text format of all annotations
	 * of the provided image.
	 *
	 * @param imageData  the image containing the measurements to retrieve
	 * @return a string representation of the measurement table
	 */
	public static String getAnnotationMeasurementTable(ImageData<?> imageData) {
		return imageData == null ? "" : getMeasurementTable(imageData, imageData.getHierarchy().getAnnotationObjects());
	}

	/**
	 * Return the measurement table in as a single tab-delimited string.
	 * This is equivalent to joining all the rows provided by {@link #getMeasurementTableRows(ImageData, Collection)}
	 * with newline characters.
	 * <p>
	 * Note that this may fail for very large tables, because the length of the text exceeds the
	 * maximum length of a Java String.
	 * In this case, using {@link #getMeasurementTableRows(ImageData, Collection)} is preferable,
	 * or alternatively pass fewer objects to measure.
	 *
	 * @param imageData  the image containing the measurements to retrieve
	 * @param pathObjects  the objects containing the measurements to retrieve
	 * @return a string representation of the measurement table
	 * @see #getMeasurementTableRows(ImageData, Collection)
	 */
	public static String getMeasurementTable(ImageData<?> imageData, Collection<? extends PathObject> pathObjects) {
		return String.join(System.lineSeparator(), getMeasurementTableRows(imageData, pathObjects));
	}

	/**
	 * Return the measurement table in a list of tab-delimited strings.
	 * <p>
	 * The first item corresponds to the header, while the rest correspond to objects in the provided collection.
	 *
	 * @param imageData  the image containing the measurements to retrieve
	 * @param pathObjects  the objects containing the measurements to retrieve
	 * @return a list of strings representing the measurement table
	 * @see #getMeasurementTable(ImageData, Collection)
	 */
	public static List<String> getMeasurementTableRows(ImageData<?> imageData, Collection<? extends PathObject> pathObjects) {
		if (imageData == null || pathObjects == null || pathObjects.isEmpty()) {
			return Collections.emptyList();
		} else {
			var table = new ObservableMeasurementTableData();
			table.setImageData(imageData, pathObjects);
			return table.getRowStrings("\t", PathTableData.DEFAULT_DECIMAL_PLACES, null);
		}
	}

	/**
	 * Create a {@link PathObject} from a GeoJSON representation.
	 *
	 * @param geoJson  the GeoJSON object to convert
	 * @return a PathObject represented by the GeoJSON object
	 */
	public static PathObject toPathObject(String geoJson) {
		return GsonTools.getInstance().fromJson(geoJson, PathObject.class);
	}

	/**
	 * Create a {@link ROI} from a GeoJSON representation.
	 *
	 * @param geoJson  the GeoJSON object to convert
	 * @return a ROI represented by the GeoJSON object
	 */
	public static ROI toROI(String geoJson) {
		return GsonTools.getInstance().fromJson(geoJson, ROI.class);
	}

	/**
	 * Create a list of {@link PathObject} from a GeoJSON representation.
	 *
	 * @param geoJson  the GeoJSON object to convert
	 * @return a list of PathObject represented by the GeoJSON object
	 */
	public static List<PathObject> toPathObjects(String geoJson) {
		return toPathObjects(GsonTools.getInstance().fromJson(geoJson, JsonElement.class));
	}

	/**
	 * Create a list of {@link PathObject} from a JSON element.
	 *
	 * @param jsonElement  the JSON element to convert
	 * @return a list of PathObject represented by the GeoJSON object
	 */
	public static List<PathObject> toPathObjects(JsonElement jsonElement) {
		if (jsonElement.isJsonArray()) {
			return toStream(jsonElement.getAsJsonArray().asList(), 10)
					.flatMap(e -> toPathObjects(e).stream())
					.toList();
		} else if (jsonElement.isJsonObject()) {
			JsonObject jsonObject = jsonElement.getAsJsonObject();

			if (jsonObject.isEmpty()) {
				return List.of();
			} else if (jsonObject.has("features")) {
				return toPathObjects(jsonObject.get("features"));
			} else {
				stripNulls(jsonObject);
				return List.of(GsonTools.getInstance().fromJson(jsonObject, PathObject.class));
			}
		} else {
			return List.of();
		}
	}

	/**
	 * Create a list of {@link ROI} from a GeoJSON representation.
	 *
	 * @param geoJson  the GeoJSON object to convert
	 * @return a list of ROI represented by the GeoJSON object
	 */
	public static List<ROI> toROIs(String geoJson) {
		return GsonTools.getInstance().fromJson(geoJson, new TypeToken<List<ROI>>() {}.getType());
	}

	/**
	 * Convert a collection of PathObjects to a GeoJSON FeatureCollection.
	 * If there is a chance the resulting string will be too long, prefer instead
	 * {@link #toFeatureCollections(Collection, int)} to partition objects into separate feature collections.
	 *
	 * @param pathObjects  the PathObjects to convert
	 * @return a GeoJSON FeatureCollection representing the provided PathObjects
	 */
	public static String toFeatureCollection(Collection<? extends PathObject> pathObjects) {
		return GsonTools.getInstance().toJson(FeatureCollection.wrap(pathObjects));
	}

	/**
	 * Convert a collection of PathObjects to GeoJSON FeatureCollections, partitioning into separate collections.
	 * This can be useful for performance reasons, and also to avoid the character limit for strings in Java and Python.
	 *
	 * @param pathObjects  the PathObjects to convert
	 * @param chunkSize  the size of each partition
	 * @return a list of GeoJSON FeatureCollection representing the provided PathObjects
	 */
	public static List<String> toFeatureCollections(Collection<? extends PathObject> pathObjects, int chunkSize) {
		return toStream(Lists.partition(new ArrayList<>(pathObjects), chunkSize), 4)
				.map(QuPathEntryPoint::toFeatureCollection)
				.toList();
	}

	/**
	 * Convert a collection of PathObjects to a list of GeoJSON objects.
	 *
	 * @param pathObjects  the PathObjects to convert
	 * @return a list of GeoJSON features representing the provided PathObjects
	 */
	public static List<String> toGeoJsonFeatureList(Collection<? extends PathObject> pathObjects) {
		return toStream(pathObjects, 100)
				.map(QuPathEntryPoint::toGeoJsonFeature)
				.toList();
	}

	/**
	 * Retrieve the IDs of a collection of PathObjects.
	 *
	 * @param pathObjects  the PathObjects whose IDs should be retrieved
	 * @return a list of IDs of the provided PathObjects
	 */
	public static List<String> getObjectIds(Collection<? extends PathObject> pathObjects) {
		return pathObjects.stream()
				.map(p -> p.getID().toString())
				.toList();
	}

	/**
	 * Get the names of all measurements of the provided PathObjects.
	 *
	 * @param pathObjects  the PathObjects whose measurement names should be retrieved
	 * @return a list of measurements names present in the provided PathObjects
	 */
	public static List<String> getMeasurementNames(Collection<? extends PathObject> pathObjects) {
		return pathObjects.stream()
				.flatMap(p -> p.getMeasurementList().getNames().stream())
				.distinct()
				.toList();
	}

	/**
	 * Get the measurement values corresponding to the provided measurement name of the
	 * provided PathObjects.
	 *
	 * @param pathObjects  the PathObjects whose measurement values should be retrieved
	 * @param name  the name of the measurement to retrieve
	 * @return a list of measurements values present in the provided PathObjects
	 */
	public static List<Double> getMeasurements(Collection<? extends PathObject> pathObjects, String name) {
		return pathObjects.stream()
				.map(p -> p.getMeasurements().getOrDefault(name, null))
				.filter(Objects::nonNull)
				.map(Number::doubleValue)
				.toList();
	}

	/**
	 * Convert a {@link PathObject} to a GeoJSON feature.
	 *
	 * @param pathObject  the PathObject to convert
	 * @return a GeoJSON feature representing the provided PathObject
	 */
	public static String toGeoJsonFeature(PathObject pathObject) {
		return GsonTools.getInstance().toJson(pathObject);
	}

	/**
	 * Convert a {@link ROI} to a GeoJSON feature.
	 *
	 * @param roi  the ROI to convert
	 * @return a GeoJSON feature representing the provided ROI
	 */
	public static String toGeoJson(ROI roi) {
		return GsonTools.getInstance().toJson(roi);
	}

	/**
	 * Get a hyperstack of an entire image at the provided downsample.
	 *
	 * @param server  the image to open
	 * @param downsample  the downsample to use when reading the image
	 * @return a TIFF encoded array of bytes corresponding to the hyperstack
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getTiffStack(ImageServer<BufferedImage> server, double downsample) throws IOException {
		return getTiffStack(server, downsample, 0, 0, server.getWidth(), server.getHeight());
	}

	/**
	 * Get a hyperstack of a portion of an image at the provided downsample.
	 *
	 * @param server  the image to open
	 * @param downsample  the downsample to use when reading the image
	 * @param x  the x-coordinate of the portion of the image to retrieve
	 * @param y  the y-coordinate of the portion of the image to retrieve
	 * @param width  the width of the portion of the image to retrieve
	 * @param height  the height of the portion of the image to retrieve
	 * @return a TIFF encoded array of bytes corresponding to the hyperstack
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getTiffStack(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height) throws IOException {
		return getTiffStack(server, downsample, x, y, width, height, 0, 0);
	}

	/**
	 * Get a hyperstack of an image for a specific region, using all z-slices and time points from 0
	 * to the ones specified in the parameters.
	 *
	 * @param server  the image to open
	 * @param downsample  the downsample to use when reading the image
	 * @param x  the x-coordinate of the portion of the image to retrieve
	 * @param y  the y-coordinate of the portion of the image to retrieve
	 * @param width  the width of the portion of the image to retrieve
	 * @param height  the height of the portion of the image to retrieve
	 * @param z  the z-stacks 0 to this parameter will be retrieved
	 * @param t  the time points 0 to this parameter will be retrieved
	 * @return a TIFF encoded array of bytes corresponding to the hyperstack
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getTiffStack(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
		return getTiffStack(
				server,
				RegionRequest.createInstance(server.getPath(), downsample, x, y, width, height, z, t)
		);
	}

	/**
	 * Get a hyperstack of an image for a specific region, using all z-slices and time points from 0
	 * to the ones specified in the provided RegionRequest.
	 *
	 * @param server  the image to open
	 * @param request  the region to read. All z-stacks from 0 to {@link RegionRequest#getZ()} and time points
	 *                 from 0 to {@link RegionRequest#getT()} will be retrieved
	 * @return a TIFF encoded array of bytes corresponding to the hyperstack
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getTiffStack(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		return toTiffBytes(IJTools.extractHyperstack(server, request));
	}

	/**
	 * Same as {@link #getTiffStack(ImageServer, double)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getTiffStackBase64(ImageServer<BufferedImage> server, double downsample) throws IOException {
		return base64Encode(getTiffStack(server, downsample));
	}

	/**
	 * Same as {@link #getTiffStack(ImageServer, double, int, int, int, int)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getTiffStackBase64(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height) throws IOException {
		return base64Encode(getTiffStack(server, downsample, x, y, width, height));
	}

	/**
	 * Same as {@link #getTiffStack(ImageServer, double, int, int, int, int, int, int)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getTiffStackBase64(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
		return base64Encode(getTiffStack(server, downsample, x, y, width, height, z, t));
	}

	/**
	 * Same as {@link #getTiffStack(ImageServer, RegionRequest)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getTiffStackBase64(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		return base64Encode(getTiffStack(server, request));
	}

	/**
	 * Read the first z-slice and first time point of the provided image at the provided downsample
	 * and return an image with the provided format.
	 *
	 * @param server  the image to open
	 * @param downsample  the downsample to use when reading the image
	 * @param format  the format the result should have
	 * @return an array of bytes described the requested image with the provided format
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getImageBytes(ImageServer<BufferedImage> server, double downsample, String format) throws IOException {
		return getImageBytes(server, downsample, 0, 0, server.getWidth(), server.getHeight(), format);
	}

	/**
	 * Read the first z-slice and first time point of a portion of the provided image at the provided downsample
	 * and return an image with the provided format.
	 *
	 * @param server  the image to open
	 * @param downsample  the downsample to use when reading the image
	 * @param x  the x-coordinate of the portion of the image to retrieve
	 * @param y  the y-coordinate of the portion of the image to retrieve
	 * @param width  the width of the portion of the image to retrieve
	 * @param height  the height of the portion of the image to retrieve
	 * @param format  the format the result should have
	 * @return an array of bytes described the requested image with the provided format
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getImageBytes(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, String format) throws IOException {
		return getImageBytes(server, downsample, x, y, width, height, 0, 0, format);
	}

	/**
	 * Read a portion of the provided image at the provided downsample and return an image with the provided format.
	 *
	 * @param server  the image to open
	 * @param downsample  the downsample to use when reading the image
	 * @param x  the x-coordinate of the portion of the image to retrieve
	 * @param y  the y-coordinate of the portion of the image to retrieve
	 * @param width  the width of the portion of the image to retrieve
	 * @param height  the height of the portion of the image to retrieve
	 * @param z  the z-slice of the image to retrieve
	 * @param t  the time point of the image to retrieve
	 * @param format  the format the result should have
	 * @return an array of bytes described the requested image with the provided format
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getImageBytes(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t, String format) throws IOException {
        return getImageBytes(
				server,
				RegionRequest.createInstance(server.getPath(), downsample, x, y, width, height, z, t),
				format
		);
	}

	/**
	 * Read a portion of the provided image and return an image with the provided format.
	 *
	 * @param server  the image to open
	 * @param request  the region to read.
	 * @param format  the format the result should have
	 * @return an array of bytes described the requested image with the provided format
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getImageBytes(ImageServer<BufferedImage> server, RegionRequest request, String format) throws IOException {
		if (isImageJFormat(format)) {
			return toTiffBytes(IJTools.convertToImagePlus(server, request).getImage());
		} else {
			return getImageBytes(server.readRegion(request), format);
		}
	}

	/**
	 * Same as {@link #getImageBytes(ImageServer, double, String)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getImageBase64(ImageServer<BufferedImage> server, double downsample, String format) throws IOException {
		return base64Encode(getImageBytes(server, downsample, format));
	}

	/**
	 * Same as {@link #getImageBytes(ImageServer, double, int, int, int, int, String)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getImageBase64(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, String format) throws IOException {
		return base64Encode(getImageBytes(server, downsample, x, y, width, height, format));
	}

	/**
	 * Same as {@link #getImageBytes(ImageServer, double, int, int, int, int, int, int, String)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getImageBase64(ImageServer<BufferedImage> server, double downsample, int x, int y, int width, int height, int z, int t, String format) throws IOException {
		return base64Encode(getImageBytes(server, downsample, x, y, width, height, z, t, format));
	}

	/**
	 * Same as {@link #getImageBytes(ImageServer, RegionRequest, String)}, but encoded with the {@link Base64} scheme.
	 */
	public static String getImageBase64(ImageServer<BufferedImage> server, RegionRequest request, String format) throws IOException {
		return base64Encode(getImageBytes(server, request, format));
	}

	/**
	 * Convert a {@link BufferedImage} to an array of bytes. If the image is RGB, the format of the returned image is PNG.
	 * Otherwise, it's "imagej tiff".
	 *
	 * @param image  the image to convert
	 * @return  an array of bytes corresponding to the provided image
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getImageBytes(BufferedImage image) throws IOException {
		return getImageBytes(image, chooseAutoFormat(image));
	}

	/**
	 * Convert a {@link BufferedImage} to an array of bytes with the provided format.
	 *
	 * @param image  the image to convert
	 * @param format  the format of the returned image
	 * @return an array of bytes corresponding to the provided image
	 * @throws IOException when an error occurs while reading the image
	 */
	public static byte[] getImageBytes(BufferedImage image, String format) throws IOException {
		if (format == null || "auto".equalsIgnoreCase(format) || format.isEmpty()) {
			format = chooseAutoFormat(image);
		}

		if (isImageJFormat(format)) {
			return toTiffBytes(IJTools.convertToUncalibratedImagePlus("Image", image));
		} else {
			try (var stream = new ByteArrayOutputStream(Math.min(1024*1024*10, image.getWidth() * image.getHeight() + 1024))) {
				ImageIO.write(image, format, stream);
				return stream.toByteArray();
			}
		}
	}

	private static String base64Encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static <T> Stream<T> toStream(Collection<T> collection, int minSizeForParallelism) {
		if (collection.size() >= minSizeForParallelism) {
			return collection.parallelStream();
		} else {
			return collection.stream();
		}
	}

	/**
	 * Strip entries that have a null value. This is needed because QuPath's v0.4.3 GeoJSON deserialization
	 * fails on some null entries.
	 *
	 * @param jsonObject  the JSON object to remove nulls from
	 */
	private static void stripNulls(JsonObject jsonObject) {
		for (String key: jsonObject.keySet()) {
			JsonElement member = jsonObject.get(key);

			if (member == null || member.isJsonNull()) {
				jsonObject.remove(key);
			} else if (member.isJsonObject()) {
				stripNulls(member.getAsJsonObject());
			}
		}
	}

	private static byte[] toTiffBytes(ImagePlus imp) {
		return new FileSaver(imp).serialize();
	}

	private static boolean isImageJFormat(String format) {
		return Set.of("imagej tiff", "imagej tif").contains(format.toLowerCase());
	}

	private static String chooseAutoFormat(BufferedImage img) {
		if (BufferedImageTools.is8bitColorType(img.getType())) {
			return "png";
		} else {
			return "imagej tiff";
		}
	}
}
