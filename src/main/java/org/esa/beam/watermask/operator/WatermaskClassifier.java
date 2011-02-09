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
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.watermask.util.ShapeFileRasterizer;

import java.awt.Point;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
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

    private TiledShapefileOpImage image;

    private final int tileSize;

    private List<Bounds> banishedGeoPos = new ArrayList<Bounds>();

    private int searchingDirection = 0;

    private String filename;
    private Map<Bounds, String> cachedEntryNames = new HashMap<Bounds, String>();
    private final boolean fill;

    /**
     * Creates a new classifier instance on the given resolution.
     *
     *
     * @param resolution The resolution, which shall be used for querying.
     *
     * @param fill If fill algorithm shall be used.
     * @throws IOException If some IO-error occurs creating the sources.
     */
    public WatermaskClassifier(int resolution, boolean fill) throws IOException {
        this.fill = fill;
        tileSize = ShapeFileRasterizer.computeSideLength(resolution);
        filename = resolution + "m.zip";
        final URL someResource = getClass().getResource(filename);
        final Properties properties = new Properties();
        int width = tileSize * 360;
        int height = tileSize * 180;
        properties.setProperty("width", "" + width);
        properties.setProperty("height", "" + height);
        properties.setProperty("tileWidth", "" + tileSize);
        properties.setProperty("tileHeight", "" + tileSize);
        image = TiledShapefileOpImage.create(VirtualDir.create(new File(someResource.getFile()).getParentFile()),
                                             properties, filename);
    }

    /**
     * Returns the sample value at the given geo-position.
     *
     * @param lat The latitude value.
     * @param lon The longitude value.
     *
     * @return The corresponding sample value.
     *
     * @throws IOException If some IO-error occurs reading the source file.
     */
    public int getWaterMaskSample(float lat, float lon) throws IOException {
        final String shapefile = getShapeFile(lat, lon);
        if (shapefile == null) {
            return fill ? getTypeOfAdjacentTile(lat, lon) : 2;
        }
        image.setShapefile(shapefile);
        final double pixelSize = 360.0 / image.getWidth();
        final int x = (int) Math.floor((lon + 180.0) / pixelSize);
        final int y = (int) Math.floor((90.0 - lat) / pixelSize);
        final Point tileIndex = new Point(x / tileSize, y / tileSize);
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
     * @throws IOException If some IO-error occurs reading the source file.
     */
    public boolean isWater(float lat, float lon) throws IOException {
        return getWaterMaskSample(lat, lon) == 1;
    }

    private byte getTypeOfAdjacentTile(float inputLat, float inputLon) {
        float lat = inputLat;
        float lon = inputLon;
        switch (searchingDirection) {
            case 0:
                // to the top
                lat = (float) ((int) lat + 1.0001);
                break;
            case 1:
                // to the left
                lon = (float) ((int) lon - 0.0001);
                break;
            case 2:
                // to the bottom
                lat = (float) ((int) lat - 0.0001);
                break;
            case 3:
                // to the right
                lon = (float) ((int) lon + 1.0001);
                break;
        }

        searchingDirection = (int) (Math.random() * 4);

        try {
            return (byte) getWaterMaskSample(lat, lon);
        } catch (IOException e) {
            throw new IllegalStateException("Error getting sample of position 'lat=" + lat + ", lon=" + lon + "'.", e);
        }
    }

    static Point geoPosToPixel(int width, int height, float lat, float lon) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = Math.abs(lat - (int) lat);
        double longitudePart = Math.abs(lon - (int) lon);
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = height - (int) (height * latitudePart) - 1;
        return new Point(xCoord, yCoord);
    }

    String getShapeFile(float lat, float lon) {
        final GeoPos geoPos = new GeoPos(lat, lon);
        final Bounds bounds = new Bounds(geoPos);
        final String cachedEntryName = cachedEntryNames.get(bounds);
        if( cachedEntryName != null ) {
            return cachedEntryName;
        }
        if (banishedGeoPos.contains(bounds)) {
            return null;
        }
        final URL imageResourceUrl = getClass().getResource(filename);
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
            if (isInRange(entryName, lat, lon)) {
                cachedEntryNames.put(bounds, entryName);
                return entryName;
            }
        }
        banishedGeoPos.add(bounds);
        return null;
    }

    static boolean isInRange(String fileName, float lat, float lon) {
        int fileLongitude = Integer.parseInt(fileName.substring(1, 4));
        int fileLatitude = Integer.parseInt(fileName.substring(5, 7));
        final int inputLongitude = (int) lon;
        final int inputLatitude = (int) lat;

        final boolean geoPosIsWest = lon < 0;
        final boolean geoPosIsSouth = lat < 0;

        if( geoPosIsWest ) {
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

        Bounds(GeoPos geoPos) {
            minX = (int) geoPos.lon;
            minY = (int) geoPos.lat;
            if (geoPos.lon < 0) {
                maxX = minX - 1;
            } else {
                maxX = minX + 1;
            }
            if (geoPos.lat < 0) {
                maxY = minY - 1;
            } else {
                maxY = minY + 1;
            }
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
