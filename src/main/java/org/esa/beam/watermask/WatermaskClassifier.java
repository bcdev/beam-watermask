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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Classifies a pixel given by its geocoordinate as water pixel.
 *
 * @author Thomas Storm
 */
public class WatermaskClassifier {

    static final String ZIP_FILENAME = "images.zip";

    public boolean isWater(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        InputStream stream = findImage(geoPos);
        final BufferedImage image = ImageIO.read(stream);
        Pixel pixel = geoPosToPixel(image.getWidth(), image.getHeight(), geoPos);
        final int sample = image.getData().getSample(pixel.x, pixel.y, 0);
        if (sample == 0) {
            return true;
        } else if (sample == 1) {
            return false;
        }
        throw new IllegalStateException("Sample value is '" + sample + "', but should be 0 or 1.");
    }

    Pixel geoPosToPixel(int width, int height, GeoPos geoPos) {
        double latitudePart = geoPos.lat - (int) geoPos.lat;
        double longitudePart = geoPos.lon - (int) geoPos.lon;
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = (int) (height * latitudePart);
        return new Pixel(xCoord, yCoord);
    }

    InputStream findImage(final GeoPos geoPos) throws IOException {
        final URL imageResourceUrl = getClass().getResource(ZIP_FILENAME);
        final ZipFile zipFile = new ZipFile(imageResourceUrl.getFile());
        final Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String entryName = entry.getName();
            if (isInRange(entryName, geoPos)) {
                return zipFile.getInputStream(entry);
            }
        }

        throw new IllegalArgumentException(
                "No image found for geo-position lat=" + geoPos.lat + ", lon=" + geoPos.lon + ".");
    }

    boolean isInRange(String fileName, GeoPos geoPos) {
        final String latPositionString = fileName.substring(1, 4);
        final String lonPositionString = fileName.substring(5, 7);
        final int fileLatitude = Integer.parseInt(latPositionString);
        final int fileLongitude = Integer.parseInt(lonPositionString);
        final int inputLatitude = (int) geoPos.lat;
        final int inputLongitude = (int) geoPos.lon;

        final boolean geoPosIsWest = geoPos.lat < 0;
        final boolean isInRange = Math.abs(inputLatitude) >= fileLatitude &&
                                  Math.abs(inputLatitude) <= fileLatitude + 1 &&
                                  Math.abs(inputLongitude) >= fileLongitude &&
                                  Math.abs(inputLongitude) <= fileLongitude + 1;

        if (fileName.startsWith("w")) {
            return geoPosIsWest && isInRange;
        } else if (fileName.startsWith("e")) {
            return !geoPosIsWest && isInRange;
        } else {
            throw new IllegalArgumentException("No valid filename: '" + fileName + "'.");
        }
    }

    static class Pixel {

        int x;
        int y;

        Pixel(int x, int y) {
            this.x = x;
            this.y = y;
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
    }

}
