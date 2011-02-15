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

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.watermask.util.ShapeFileRasterizer;

import java.awt.Point;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileOutputStream;
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

    public static final int LAND_VALUE = 0;
    public static final int WATER_VALUE = 1;
    public static final int INVALID_VALUE = 2;
    public static final int RESOLUTION_50 = 50;
    public static final int RESOLUTION_150 = 150;

    private final int tileSize;
    private final boolean fill;

    private List<Bounds> banishedGeoPos = new ArrayList<Bounds>();
    private TiledShapefileOpImage image;
    private int searchingDirection = 0;
    private String filename;
    private String zipfilePath;
    private Map<Bounds, String> cachedEntryNames = new HashMap<Bounds, String>();
    private HashMap<Point, String> tileShapefileMap;
    private Map<Bounds, Byte> fillDataMap;

    /**
     * Creates a new classifier instance on the given resolution.
     *
     * @param resolution The resolution specifying on source data is to be queried. Needs to be RESOLUTION_50 or
     *                   RESOLUTION_150.
     * @param fill       If fill algorithm shall be used.
     *
     * @throws IOException If some IO-error occurs creating the sources.
     */
    public WatermaskClassifier(int resolution, boolean fill) throws IOException {
        if (resolution != RESOLUTION_50 && resolution != RESOLUTION_150) {
            throw new IllegalArgumentException(
                    "Resolution needs to be " + RESOLUTION_50 + " or " + RESOLUTION_150 + ".");
        }
        this.fill = fill;
        tileSize = ShapeFileRasterizer.computeSideLength(resolution);
        filename = resolution + "m.zip";
        zipfilePath = getZipfilePath();
        tileShapefileMap = new HashMap<Point, String>();
        fillDataMap = new HashMap<Bounds, Byte>();

        int width = tileSize * 360;
        int height = tileSize * 180;
        final Properties properties = new Properties();
        properties.setProperty("width", "" + width);
        properties.setProperty("height", "" + height);
        properties.setProperty("tileWidth", "" + tileSize);
        properties.setProperty("tileHeight", "" + tileSize);
        final URL imageProperties = getClass().getResource("image.properties");
        properties.load(imageProperties.openStream());

        image = TiledShapefileOpImage.create(properties, zipfilePath, this);
    }

    /**
     * Returns the sample value at the given geo-position.
     *
     * @param lat The latitude value.
     * @param lon The longitude value.
     *
     * @return 0 if the given position is over land, 1 if it is over water, 2 if no definite statement can be made
     *         about the position.
     *
     * @throws IOException If some IO-error occurs reading the source file.
     */
    public int getWaterMaskSample(float lat, float lon) throws IOException {
        if (lat >= 60.0 || lat <= -60.0) {
            // no shapefiles for latitudes  >= 60° or <= -60°
            return INVALID_VALUE;
        }
        final String shapefile = getShapeFile(lat, lon);
        if (shapefile == null) {
            final Bounds bounds = new Bounds(lat, lon);
            final Byte cachedValue = fillDataMap.get(bounds);
            if (cachedValue != null) {
                return cachedValue;
            }
            final byte value = fill ? getTypeOfAdjacentTile(lat, lon) : INVALID_VALUE;
            fillDataMap.put(bounds, value);
            return value;
        }
        final double pixelSize = 360.0 / image.getWidth();
        final int x = (int) Math.floor((lon + 180.0) / pixelSize);
        final int y = (int) Math.floor((90.0 - lat) / pixelSize);
        final Point tileIndex = new Point(x / tileSize, y / tileSize);
        tileShapefileMap.put(tileIndex, shapefile);
        final Raster tile = image.getTile(tileIndex.x, tileIndex.y);
        return tile.getSample(x, y, 0);
    }

    /**
     * Classifies the given geo-position as water or land.
     *
     * @param lat The latitude value.
     * @param lon The longitude value.
     *
     * @return true, if the geo-position is over water, false otherwise.
     *
     * @throws IOException              If some IO-error occurs reading the source file.
     * @throws IllegalArgumentException If there is no definite statement possible for the given position.
     */
    public boolean isWater(float lat, float lon) throws IOException {
        final int waterMaskSample = getWaterMaskSample(lat, lon);
        if (waterMaskSample == INVALID_VALUE) {
            throw new IllegalArgumentException(
                    "No definite statement possible for position 'lat=" + lat + ", lon=" + lon + "'.");
        }
        return waterMaskSample == WATER_VALUE;
    }

    private byte getTypeOfAdjacentTile(float inputLat, float inputLon) {
        float lat = inputLat;
        float lon = inputLon;
        switch (searchingDirection) {
            case 0:
                // to the top
                lat = (float) ((int) lat + 1.00001);
                break;
            case 1:
                // to the left
                lon = (float) ((int) lon - 0.00001);
                break;
            case 2:
                // to the bottom
                lat = (float) ((int) lat - 0.00001);
                break;
            case 3:
                // to the right
                lon = (float) ((int) lon + 1.00001);
                break;
        }

        searchingDirection = (int) (Math.random() * 4);

        try {
            final byte waterMaskSample = (byte) getWaterMaskSample(lat, lon);
            return waterMaskSample == INVALID_VALUE ? getTypeOfAdjacentTile(lat, lon) : waterMaskSample;
        } catch (IOException e) {
            throw new IllegalStateException("Error getting sample of position 'lat=" + lat + ", lon=" + lon + "'.", e);
        }
    }

    String getShapefile(Point tile) {
        return tileShapefileMap.get(tile);
    }

    static Point geoPosToPixel(int width, int height, float lat, float lon) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = Math.abs(lat - (int) lat);
        double longitudePart = Math.abs(lon - (int) lon);
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = height - (int) (height * latitudePart) - 1;
        return new Point(xCoord, yCoord);
    }

    String getShapeFile(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        final Bounds bounds = new Bounds(geoPos);
        final String cachedEntryName = cachedEntryNames.get(bounds);
        if (cachedEntryName != null) {
            return cachedEntryName;
        }
        if (banishedGeoPos.contains(bounds)) {
            return null;
        }
        ZipFile zipFile = new ZipFile(zipfilePath);
        final Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String entryName = entry.getName();
            if (isInRange(entryName, lat, lon)) {
                cachedEntryNames.put(bounds, entryName);
                return entryName;
            }
        }
        banishedGeoPos.add(bounds);
        return null;
    }

    private String getZipfilePath() throws IOException {
        final String zipfilePath = System.getProperty("java.io.tmpdir") + filename;
        if (new File(zipfilePath).exists()) {
            return zipfilePath;
        }
        final InputStream zipFileAsStream = getClass().getResourceAsStream(filename);
        final FileOutputStream outputStream = new FileOutputStream(zipfilePath);
        byte[] buffer = new byte[8092];
        int amount;
        while ((amount = zipFileAsStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, amount);
        }
        outputStream.close();
        zipFileAsStream.close();
        return zipfilePath;
    }

    static boolean isInRange(String fileName, float lat, float lon) {
        int fileLongitude = Integer.parseInt(fileName.substring(1, 4));
        int fileLatitude = Integer.parseInt(fileName.substring(5, 7));
        final int inputLongitude = (int) lon;
        final int inputLatitude = (int) lat;

        final boolean geoPosIsWest = lon < 0;
        final boolean geoPosIsSouth = lat < 0;

        if (geoPosIsWest) {
            fileLongitude--;
        }
        if (geoPosIsSouth) {
            fileLatitude--;
        }

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
            throw new IllegalArgumentException("No file for geo-position: 'lat=" + lat + ", lon=" + lon + "'.");
        }
    }

    static class Bounds {

        private int minX;
        private int maxX;
        private int minY;
        private int maxY;

        Bounds(float lat, float lon) {
            minX = (int) lon;
            minY = (int) lat;
            if (lon < 0) {
                maxX = minX - 1;
            } else {
                maxX = minX + 1;
            }
            if (lat < 0) {
                maxY = minY - 1;
            } else {
                maxY = minY + 1;
            }
        }

        Bounds(GeoPos geoPos) {
            this(geoPos.lat, geoPos.lon);
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

            return maxX == bounds.maxX && maxY == bounds.maxY && minX == bounds.minX && minY == bounds.minY;

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
