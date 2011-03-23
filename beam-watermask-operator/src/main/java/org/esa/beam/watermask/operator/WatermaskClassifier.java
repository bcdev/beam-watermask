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
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

import java.awt.Point;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

/**
 * Classifies a pixel given by its geo-coordinate as water pixel.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored"})
public class WatermaskClassifier {

    public static final int WATER_VALUE = 1;
    public static final int INVALID_VALUE = 2;
    public static final int RESOLUTION_50 = 50;
    public static final int RESOLUTION_150 = 150;

    private final TiledShapefileOpImage image;

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
     * @throws java.io.IOException If some IO-error occurs creating the sources.
     */
    public WatermaskClassifier(int resolution) throws IOException {
        if (resolution != RESOLUTION_50 && resolution != RESOLUTION_150) {
            throw new IllegalArgumentException(
                    "Resolution needs to be " + RESOLUTION_50 + " or " + RESOLUTION_150 + ".");
        }

        final File auxdataDir = installAuxdata();
        int tileSize = WatermaskUtils.computeSideLength(resolution);

        int width = tileSize * 360;
        int height = tileSize * 180;
        final Properties properties = new Properties();
        properties.setProperty("width", String.valueOf(width));
        properties.setProperty("height", String.valueOf(height));
        properties.setProperty("tileWidth", String.valueOf(tileSize));
        properties.setProperty("tileHeight", String.valueOf(tileSize));
        final URL imageProperties = getClass().getResource("image.properties");
        properties.load(imageProperties.openStream());

        File zipFilePath = new File(auxdataDir, resolution + "m.zip");
        image = TiledShapefileOpImage.create(properties, zipFilePath);
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
        final double pixelSize = 360.0 / image.getWidth();
        final int x = (int) Math.floor((lon + 180.0) / pixelSize);
        final int y = (int) Math.floor((90.0 - lat) / pixelSize);
        final Raster tile = image.getTile(image.XToTileX(x), image.YToTileY(y));
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
     */
    public boolean isWater(float lat, float lon) throws IOException {
        final int waterMaskSample = getWaterMaskSample(lat, lon);
        return waterMaskSample == WATER_VALUE;
    }

    static Point geoPosToPixel(int width, int height, float lat, float lon) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = Math.abs(lat - (int) lat);
        double longitudePart = Math.abs(lon - (int) lon);
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = height - (int) (height * latitudePart) - 1;
        return new Point(xCoord, yCoord);
    }

}
