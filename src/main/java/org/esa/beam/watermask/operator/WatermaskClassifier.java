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
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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

    private TiledShapefileOpImage image;

    private final int IMAGE_LENGTH;

    private List<Bounds> banishedGeoPos = new ArrayList<Bounds>();

    public WatermaskClassifier(int sideLength) throws IOException {
        IMAGE_LENGTH = sideLength;
        final URL someResource = getClass().getResource("images.zip");
        final Properties properties = new Properties();
        properties.setProperty("width", "368640");
        properties.setProperty("height", "184320");
        properties.setProperty("tileWidth", "" + (int) (IMAGE_LENGTH / Math.sqrt(8.0)));
        properties.setProperty("tileHeight", "" + (int) (IMAGE_LENGTH / Math.sqrt(8.0)));
        // TODO - read properties from file
        image = TiledShapefileOpImage.create(VirtualDir.create(new File(someResource.getFile()).getParentFile()),
                                             properties);
    }

    public int getWaterMaskSample(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        final Point pixelPosition = geoPosToPixel((int) (IMAGE_LENGTH / Math.sqrt(8.0)),
                                                  (int) (IMAGE_LENGTH / Math.sqrt(8.0)), geoPos);
        final Point tileIndex = computeTileIndex(geoPos);
        final Raster tile = image.getTile(tileIndex.x, tileIndex.y);
        final SampleModel sampleModel = tile.getSampleModel();
        final DataBuffer dataBuffer = tile.getDataBuffer();
        final int sample = sampleModel.getSample(pixelPosition.x, pixelPosition.y, 0, dataBuffer);
        int bitOffset = computeBitOffset(geoPos);
        final String string = toBinaryString(sample);
        return Integer.parseInt("" + string.charAt(bitOffset));
    }

    public boolean isWater(float lat, float lon) throws IOException {
        return getWaterMaskSample(lat, lon) == 1;
    }

    private int computeBitOffset(GeoPos geoPos) {
        int offset = geoPosToPixel((int) (IMAGE_LENGTH * Math.sqrt(8.0)),
                                   (int) (IMAGE_LENGTH * Math.sqrt(8.0)),
                                   geoPos).x;
        offset = 7 - offset % 8;
        return offset;
    }

    static String toBinaryString(int n) {
        StringBuilder sb = new StringBuilder("00000000");
        for (int bit = 0; bit < 8; bit++) {
            if (((n >> bit) & 1) > 0) {
                sb.setCharAt(7 - bit, '1');
            }
        }
        return sb.toString();
    }


    Point computeTileIndex(GeoPos geoPos) {
        int lonOffset = 0;
        if (geoPos.lon >= 0) {
            lonOffset = 1;
        }
        int latOffset = 0;
        if (geoPos.lat < 0) {
            latOffset = 1;
        }

        final int x = (int) (geoPos.lon) + 179 + lonOffset;
        final int y = Math.abs((int) (geoPos.lat) - 89) + latOffset;
        return new Point(x, y);
    }

    Point geoPosToPixel(int width, int height, GeoPos geoPos) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = Math.abs(geoPos.lat - (int) geoPos.lat);
        double longitudePart = Math.abs(geoPos.lon - (int) geoPos.lon);
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = height - (int) (height * latitudePart) - 1;
        return new Point(xCoord, yCoord);
    }

    boolean shapeFileExists(GeoPos geoPos) {
        final Bounds bounds = new Bounds(geoPos);
        if (banishedGeoPos.contains(bounds)) {
            return false;
        }
        final URL imageResourceUrl = getClass().getResource(ZIP_FILENAME);
        ZipFile zipFile;
        try {
            zipFile = new ZipFile(imageResourceUrl.getFile());
        } catch (IOException e) {
            throw new IllegalStateException("File '" + imageResourceUrl.getFile() + "' not found.");
        }
        final Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String entryName = entry.getName();
            if (isInRange(entryName, geoPos)) {
                return true;
            }
        }
        banishedGeoPos.add(bounds);
        return false;
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
            throw new IllegalArgumentException("No file for geo-position: '" + geoPos.toString() + "'.");
        }
    }

    static class GeoPos {

        double lat;
        double lon;

        GeoPos(double lat, double lon) {
            if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                throw new IllegalArgumentException(
                        "Lon has to be between -180 and 180, and lat has to be between -90 and 90, respectively.");
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
