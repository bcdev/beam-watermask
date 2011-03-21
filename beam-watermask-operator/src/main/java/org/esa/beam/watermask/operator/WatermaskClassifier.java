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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.watermask.util.ShapeFileRasterizer;

import java.awt.Point;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

/**
 * Classifies a pixel given by its geocoordinate as water pixel.
 *
 * @author Thomas Storm
 */
@SuppressWarnings({"ResultOfMethodCallIgnored"})
public class WatermaskClassifier {

    public static final int WATER_VALUE = 1;
    public static final int INVALID_VALUE = 2;
    public static final int RESOLUTION_50 = 50;
    public static final int RESOLUTION_150 = 150;

    private final int tileSize;
    private final boolean fill;
    private final int resolution;
    private final File zipfilePath;

    private final List<Bounds> banishedGeoPos;
    private final TiledShapefileOpImage image;
    private final AtomicInteger searchingDirection;
    private final Map<Bounds, String> cachedEntryNames;
    private final Map<Point, String> tileShapefileMap;
    private final Map<Bounds, Byte> fillDataMap;

    /**
     * Creates a new classifier instance on the given resolution.
     * The classifier uses a tiled image in background to determine the if a
     * given geo-position is over land or over water.
     * Tiles do not exist if the whole region of the tile would only cover land or water.
     * Were a tile does not exist a so called fill algorithm can be performed.
     * In this case the next existing tile is searched and the nearest classification value
     * for the given geo-position is returned.
     * If the fill algorithm is not performed a value indicating invalid is returned.
     *
     * @param resolution The resolution specifying on source data is to be queried. Needs to be RESOLUTION_50 or
     *                   RESOLUTION_150.
     * @param fill       If fill algorithm shall be used.
     *
     * @throws java.io.IOException If some IO-error occurs creating the sources.
     */
    public WatermaskClassifier(int resolution, boolean fill) throws IOException {
        if (resolution != RESOLUTION_50 && resolution != RESOLUTION_150) {
            throw new IllegalArgumentException(
                    "Resolution needs to be " + RESOLUTION_50 + " or " + RESOLUTION_150 + ".");
        }

        final File auxdataDir = installAuxdata();
        this.resolution = resolution;
        this.fill = fill;
        tileSize = ShapeFileRasterizer.computeSideLength(resolution);
        searchingDirection = new AtomicInteger(0);
        banishedGeoPos = Collections.synchronizedList(new ArrayList<Bounds>());
        cachedEntryNames = Collections.synchronizedMap(new HashMap<Bounds, String>());
        tileShapefileMap = Collections.synchronizedMap(new HashMap<Point, String>());
        fillDataMap = Collections.synchronizedMap(new HashMap<Bounds, Byte>());

        int width = tileSize * 360;
        int height = tileSize * 180;
        final Properties properties = new Properties();
        properties.setProperty("width", "" + width);
        properties.setProperty("height", "" + height);
        properties.setProperty("tileWidth", "" + tileSize);
        properties.setProperty("tileHeight", "" + tileSize);
        final URL imageProperties = getClass().getResource("image.properties");
        properties.load(imageProperties.openStream());

        zipfilePath = new File(auxdataDir, resolution + "m.zip");
        image = TiledShapefileOpImage.create(properties, zipfilePath, this);
    }

    private File installAuxdata() throws IOException {
        String auxdataSrcPath = "auxdata/images";
        final String relativeDestPath = ".beam/" + "beam-watermask-operator" + "/" + auxdataSrcPath;
        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), relativeDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", ProgressMonitor.NULL);

        return auxdataTargetDir;
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
     * @throws java.io.IOException If some IO-error occurs reading the source file.
     */
    public int getWaterMaskSample(float lat, float lon) throws IOException {
        if (lat >= 60.0 || lat <= -60.0) {
            // no shapefiles for latitudes  >= 60° or <= -60°
            return INVALID_VALUE;
        }
        final String imgFileName = getImgFileName(lat, lon);
        if (imgFileName == null) {
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
        tileShapefileMap.put(tileIndex, imgFileName);
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
     * @throws java.io.IOException If some IO-error occurs reading the source file.
     * @throws Exception           If there is no definite statement possible for the given position.
     */
    public boolean isWater(float lat, float lon) throws Exception {
        final int waterMaskSample = getWaterMaskSample(lat, lon);
        if (waterMaskSample == INVALID_VALUE) {
            throw new Exception(
                    "No definite statement possible for position 'lat=" + lat + ", lon=" + lon + "'.");
        }
        return waterMaskSample == WATER_VALUE;
    }

    private byte getTypeOfAdjacentTile(float inputLat, float inputLon) {
        float lat = inputLat;
        float lon = inputLon;
        switch (searchingDirection.get()) {
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

        searchingDirection.set((int) (Math.random() * 4));

        try {
            final byte waterMaskSample = (byte) getWaterMaskSample(lat, lon);
            return waterMaskSample == INVALID_VALUE ? getTypeOfAdjacentTile(lat, lon) : waterMaskSample;
        } catch (IOException e) {
            throw new IllegalStateException("Error getting sample of position 'lat=" + lat + ", lon=" + lon + "'.", e);
        }
    }

    String getImgFileName(Point tile) {
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

    String getImgFileName(final float lat, final float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        final Bounds bounds = new Bounds(geoPos);
        final String cachedEntryName = cachedEntryNames.get(bounds);
        if (cachedEntryName != null) {
            return cachedEntryName;
        }
        if (banishedGeoPos.contains(bounds)) {
            return null;
        }

        final String imgFileName = createImgFileName(lat, lon);
        if (!existImgFile(imgFileName)) {
            banishedGeoPos.add(bounds);
            return null;
        }

        cachedEntryNames.put(bounds, imgFileName);
        return imgFileName;
    }

    private boolean existImgFile(String imgFileName) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zipfilePath);
            return zipFile.getEntry(imgFileName) != null;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    String createImgFileName(float lat, float lon) {
        final boolean geoPosIsWest = lon < 0;
        final boolean geoPosIsSouth = lat < 0;
        StringBuilder result = new StringBuilder();
        final String eastOrWest = geoPosIsWest ? "w" : "e";
        result.append(eastOrWest);
        lon -= geoPosIsWest ? 1 : 0;
        lat -= geoPosIsSouth ? 1 : 0;
        int positiveLon = Math.abs((int) lon);
        if (positiveLon >= 10 && positiveLon < 100) {
            result.append("0");
        } else if (positiveLon < 10) {
            result.append("00");
        }
        result.append(positiveLon);

        final String northOrSouth = geoPosIsSouth ? "s" : "n";
        result.append(northOrSouth);

        final int positiveLat = Math.abs((int) lat);
        if (positiveLat < 10) {
            result.append("0");
        }
        result.append(positiveLat);
        result.append(".img");

        return result.toString();
    }

    public int getResolution() {
        return resolution;
    }

    private static class Bounds {

        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;

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
