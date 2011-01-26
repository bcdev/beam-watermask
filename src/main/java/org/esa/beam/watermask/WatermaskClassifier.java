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

package org.esa.beam.watermask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;

/**
 * Classifies a pixel given by its geocoordinate as water pixel.
 *
 * @author Thomas Storm
 */
public class WatermaskClassifier {

    public boolean isWater(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        File file = findImage(geoPos);
        final BufferedImage image = ImageIO.read(file);
        Pixel pixel = geoPosToPixel(geoPos);
        // TODO - validate
        final int sample = image.getData().getSample(pixel.x, pixel.y, 0);
        if (sample == 0) {
            return true;
        } else if (sample == 1) {
            return false;
        }
        throw new IllegalStateException("Sample value is '" + sample + "', but should be 0 or 1.");
    }

    private Pixel geoPosToPixel(GeoPos geoPos) {
        return null;
    }

    private File findImage(final GeoPos geoPos) {
        final URL someResource = getClass().getResource("e000n05f.zip");
        final File resourceDir = new File(someResource.getFile()).getParentFile();
        final File[] files = resourceDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String fileName) {
                return fileName.endsWith(".zip") && isInRange(fileName, geoPos);
            }
        });
        return null;
    }

    private boolean isInRange(String fileName, GeoPos geoPos) {
        String[] splittedString = fileName.split("n");
        return false;
    }

    static class Pixel {

        int x;
        int y;

        private Pixel(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static class GeoPos {

        float lat;
        float lon;

        private GeoPos(float lat, float lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

}
