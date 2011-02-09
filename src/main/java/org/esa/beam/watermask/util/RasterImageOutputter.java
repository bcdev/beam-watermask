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

package org.esa.beam.watermask.util;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * TODO fill out or delete
 *
 * @author Thomas Storm
 */
public class RasterImageOutputter {

    private static final int TILE_WIDTH = ShapeFileRasterizer.computeSideLength(150);

    public static void main(String[] args) throws IOException {

        final InputStream inputStream;
        if(args.length == 3) {
            ZipFile zipFile = new ZipFile(args[0]);
            String shapefile = args[1];
            final ZipEntry entry = zipFile.getEntry(shapefile);
            inputStream = zipFile.getInputStream(entry);
        } else {
            inputStream = new FileInputStream(args[0]);
        }

        WritableRaster targetRaster = WritableRaster.createPackedRaster(0, TILE_WIDTH, TILE_WIDTH, 1, 1,
                                                                        new Point(0, 0));

        final byte[] data = ((DataBufferByte) targetRaster.getDataBuffer()).getData();
        inputStream.read(data);
        final BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_WIDTH, BufferedImage.TYPE_BYTE_BINARY);
        image.setData(targetRaster);
        ImageIO.write(image, "png", new File(args[args.length - 1]));
    }
}