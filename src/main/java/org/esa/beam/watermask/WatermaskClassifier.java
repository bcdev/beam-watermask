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

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.SampleModel;
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

    private final int SIDE_LENGTH;

    public WatermaskClassifier() {
        SIDE_LENGTH = 1024;
    }

    public WatermaskClassifier(int sideLength) {
        SIDE_LENGTH = sideLength;
    }

    public boolean isWater(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        // TODO - cache images
        InputStream stream = findImage(geoPos);
        final BufferedImage bufferedImage = readImage(stream);
        final Point point = geoPosToPixel(SIDE_LENGTH, SIDE_LENGTH, geoPos);
        final SampleModel sampleModel = bufferedImage.getSampleModel();
        final DataBuffer dataBuffer = bufferedImage.getData().getDataBuffer();
        final int sample = sampleModel.getSample(point.x, point.y, 0, dataBuffer);
        return sample == 1;
    }

    BufferedImage readImage(final InputStream inputStream) throws IOException {
        BufferedImage image = new BufferedImage(SIDE_LENGTH, SIDE_LENGTH, BufferedImage.TYPE_BYTE_BINARY);
        final byte[] buffer = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        inputStream.read(buffer);
        inputStream.close();
        return image;
    }

    Point geoPosToPixel(int width, int height, GeoPos geoPos) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = Math.abs(geoPos.lat - (int) geoPos.lat);
        double longitudePart = Math.abs(geoPos.lon - (int) geoPos.lon);
        final int xCoord = (int) (width * longitudePart);
        final int yCoord = height - (int) (height * latitudePart) - 1;
        return new Point(xCoord, yCoord);
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
        final String lonPositionString = fileName.substring(1, 4);
        final String latPositionString = fileName.substring(5, 7);
        final int fileLongitude = Integer.parseInt(lonPositionString);
        final int fileLatitude = Integer.parseInt(latPositionString);
        final int inputLongitude = (int) geoPos.lon;
        final int inputLatitude = (int) geoPos.lat;

        final boolean geoPosIsWest = geoPos.lon < 0;
        final boolean isInRange = Math.abs(inputLatitude) >= fileLatitude &&
                                  Math.abs(inputLatitude) < fileLatitude + 1 &&
                                  Math.abs(inputLongitude) >= fileLongitude &&
                                  Math.abs(inputLongitude) < fileLongitude + 1;

        if (fileName.startsWith("w")) {
            return geoPosIsWest && isInRange;
        } else if (fileName.startsWith("e")) {
            return !geoPosIsWest && isInRange;
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

}
