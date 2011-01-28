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
    private static final int METERS_PER_PIXEL = 30;
    private static final int HORIZONTAL_SHAPEFILE_COUNT = 360;
    private static final int VERTICAL_SHAPEFILE_COUNT = 180;

    public boolean isWater(float lat, float lon) throws IOException {
        final GeoPos geoPos = new GeoPos(lat, lon);
        // TODO - cache images
        InputStream stream = findImage(geoPos);
        final Point imageSize = computeImagePixelCount();
        Point pixelPosition = geoPosToPixel(imageSize.x, imageSize.y, geoPos);
        int offset = computeOffset(pixelPosition, imageSize);
        byte[] buffer = new byte[1];
        int counter = 0;
        while (counter < offset) {
            stream.read(buffer);
            counter++;
        }
        stream.close();
        final byte byteSample = buffer[0];
        int sample = byteSample & 0xFF;
        System.out.println("sample = " + sample);
        return (sample % 2 == 1);
    }

    int computeOffset(Point position, Point imageSize) {
        if( position.x >= imageSize.x || position.y >= imageSize.y) {
            throw new IllegalArgumentException("Position '" + position.toString() + "' is not inside the image bounds '" + imageSize.toString() + "'.");
        }
        // divide by 8 because the image has been written in byte-chunks, but we want to read bit-wise
        return (position.y * imageSize.x + position.x) / 8;
    }

    Point geoPosToPixel(int width, int height, GeoPos geoPos) {
        // exploiting that shapefiles are of size '1° squared'
        double latitudePart = geoPos.lat - (int) geoPos.lat;
        double longitudePart = geoPos.lon - (int) geoPos.lon;
        final int xCoord = Math.abs((int) (width * longitudePart));
        final int yCoord = Math.abs((int) (height * latitudePart));
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

    static Point computeImagePixelCount() {
        // Resolution: 30 meters. Datum: WGS84. Projection: Geographic lat/lon. Image size: 1° squared.
//        final Ellipsoid ellipsoid = DefaultGeographicCRS.WGS84.getDatum().getEllipsoid();
//        double horizontalCircumference = (int) (2 * Math.PI * ellipsoid.getSemiMajorAxis());
//        final double horizontalPixelCount = horizontalCircumference / METERS_PER_PIXEL;
//        int width = (int) (horizontalPixelCount / HORIZONTAL_SHAPEFILE_COUNT);
//        double verticalCircumference = (int) (2 * Math.PI * ellipsoid.getSemiMinorAxis());
//        final double verticalPixelCount = verticalCircumference / METERS_PER_PIXEL;
//        int height = (int) (verticalPixelCount / VERTICAL_SHAPEFILE_COUNT);
        return new Point(1024, 1024);
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
