/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.watermask.operator;

import com.bc.ceres.core.VirtualDir;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Classifies a pixel given by its geocoordinate as water pixel.
 *
 * @author Thomas Storm
 */
@SuppressWarnings({"ResultOfMethodCallIgnored"})
public class WatermaskClassifier {

    public static final String ZIP_FILENAME = "images.zip";

    private final int SIDE_LENGTH;
    private final Map<Bounds, BufferedImage> cachedImages = new HashMap<Bounds, BufferedImage>();
    private List<Bounds> banishedGeoPos = new ArrayList<Bounds>();
    private TiledShapefileOpImage image;

    public WatermaskClassifier(int sideLength) throws IOException {
        SIDE_LENGTH = sideLength;
        final URL someResource = getClass().getResource("images.zip");
        final Properties properties = new Properties();
        properties.setProperty("width", "368640");
        properties.setProperty("height", "184320");
        properties.setProperty("tileWidth", "1024");
        properties.setProperty("tileHeight", "1024");
        // TODO - read properties from file
        image = TiledShapefileOpImage.create(VirtualDir.create(new File(someResource.getFile()).getParentFile()),
                                             properties);
    }

    public boolean isWater(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        final Point point = geoPosToPixel(SIDE_LENGTH, SIDE_LENGTH, geoPos);
        final SampleModel sampleModel = image.getSampleModel();
        final DataBuffer dataBuffer = image.getData().getDataBuffer();
        final int sample = sampleModel.getSample(point.x, point.y, 0, dataBuffer);
        return sample == 1;

//        final BufferedImage bufferedImage = findImage(geoPos);
//        final Point point = geoPosToPixel(SIDE_LENGTH, SIDE_LENGTH, geoPos);
//        final SampleModel sampleModel = bufferedImage.getSampleModel();
//        final DataBuffer dataBuffer = bufferedImage.getData().getDataBuffer();
//        final int sample = sampleModel.getSample(point.x, point.y, 0, dataBuffer);
//        return sample == 1;
    }

    Point geoPosToPixel(int width, int height, GeoPos geoPos) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = Math.abs(geoPos.lat - (int) geoPos.lat);
        double longitudePart = Math.abs(geoPos.lon - (int) geoPos.lon);
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = height - (int) (height * latitudePart) - 1;
        return new Point(xCoord, yCoord);
    }

    BufferedImage findImage(final GeoPos geoPos) throws IOException {
        BufferedImage image = getAlreadyComputedImage(geoPos);
        if (image != null) {
            return image;
        }
        if (banishedGeoPos.contains(new Bounds(geoPos))) {
            throw new IllegalArgumentException(
                    "No image found for geo-position lat=" + geoPos.lat + ", lon=" + geoPos.lon + ".");
        }
        final URL imageResourceUrl = getClass().getResource(ZIP_FILENAME);
        final ZipFile zipFile = new ZipFile(imageResourceUrl.getFile());
        final Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String entryName = entry.getName();
            if (isInRange(entryName, geoPos)) {
                final BufferedImage bufferedImage = readImage(zipFile.getInputStream(entry));
                cachedImages.put(new Bounds(geoPos), bufferedImage);
                return bufferedImage;
            } else {
                banishedGeoPos.add(new Bounds(geoPos));
            }
        }
        throw new IllegalArgumentException(
                "No image found for geo-position lat=" + geoPos.lat + ", lon=" + geoPos.lon + ".");
    }

    private BufferedImage getAlreadyComputedImage(GeoPos geoPos) {
        final Bounds key = new Bounds(geoPos);
        if (cachedImages.size() == 0 || !cachedImages.containsKey(key)) {
            return null;
        }
        return cachedImages.get(key);
    }

    BufferedImage readImage(final InputStream inputStream) throws IOException {
        BufferedImage image = new BufferedImage(SIDE_LENGTH, SIDE_LENGTH, BufferedImage.TYPE_BYTE_BINARY);
        final byte[] buffer = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        inputStream.read(buffer);
        inputStream.close();
        return image;
    }

    boolean isInRange(String fileName, GeoPos geoPos) {
        final String lonPositionString = fileName.substring(1, 4);
        final String latPositionString = fileName.substring(5, 7);
        final int fileLongitude = Integer.parseInt(lonPositionString);
        final int fileLatitude = Integer.parseInt(latPositionString);
        final int inputLongitude = (int) geoPos.lon;
        final int inputLatitude = (int) geoPos.lat;

        final boolean geoPosIsWest = geoPos.lon < 0;
        final boolean geoPosIsSouth = geoPos.lat < 0;
        final boolean isInRange = Math.abs(inputLatitude) >= fileLatitude &&
                                  Math.abs(inputLatitude) < fileLatitude + 1 &&
                                  Math.abs(inputLongitude) >= fileLongitude &&
                                  Math.abs(inputLongitude) < fileLongitude + 1;
        if (!isInRange) {
            return false;
        }

        if (fileName.startsWith("w") && fileName.charAt(4) == 'n') {
            return geoPosIsWest && !geoPosIsSouth;
        } else if (fileName.startsWith("e") && fileName.charAt(4) == 'n') {
            return !geoPosIsWest && !geoPosIsSouth;
        } else if (fileName.startsWith("w") && fileName.charAt(4) == 's') {
            return geoPosIsWest && geoPosIsSouth;
        } else if (fileName.startsWith("e") && fileName.charAt(4) == 's') {
            return !geoPosIsWest && geoPosIsSouth;
        } else {
            throw new IllegalArgumentException("No valid filename: '" + fileName + "'.");
        }
    }

    static class GeoPos {

        double lat;
        double lon;

        GeoPos(double lat, double lon) {
            if (lat < -180 || lat > 180 || lon < -90 || lon > 90) {
                throw new IllegalArgumentException(
                        "Lat has to be between -180 and 180, and lon has to be between -90 and 90, respectively.");
            }
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return "GeoPos{" +
                   "lat=" + lat +
                   ", lon=" + lon +
                   '}';
        }
    }

    private static class Bounds {

        int minX;
        int maxX;
        int minY;
        int maxY;

        private Bounds(GeoPos geoPos) {
            minX = (int) geoPos.lon;
            maxX = minX + 1;
            minY = (int) geoPos.lat;
            maxY = minY + 1;
        }

        boolean isInRange(GeoPos geoPos) {
            return isInXRange(geoPos.lon) && isInYRange(geoPos.lat);
        }

        boolean isInXRange(double value) {
            return minX <= value && maxX >= value;
        }

        boolean isInYRange(double value) {
            return minY <= value && maxY >= value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Bounds bounds = (Bounds) o;

            if (maxX != bounds.maxX) {
                return false;
            }
            if (maxY != bounds.maxY) {
                return false;
            }
            if (minX != bounds.minX) {
                return false;
            }
            if (minY != bounds.minY) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = minX;
            result = 31 * result + maxX;
            result = 31 * result + minY;
            result = 31 * result + maxY;
            return result;
        }
    }
}
