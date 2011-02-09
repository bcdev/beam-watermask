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
import org.esa.beam.jai.ImageHeader;

import javax.media.jai.JAI;
import javax.media.jai.SourcelessOpImage;
import java.awt.Point;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Responsible for tiled access on the data.
 *
 * @author Thomas Storm
 */
public class TiledShapefileOpImage extends SourcelessOpImage {

    private ZipFile zipFile;

    public static TiledShapefileOpImage create(VirtualDir imageDir,
                                               Properties defaultImageProperties,
                                               String filename) throws IOException {
        final ImageHeader imageHeader = ImageHeader.load(new File(imageDir.getBasePath()), defaultImageProperties);
        return new TiledShapefileOpImage(imageHeader, null, imageDir, filename);
    }

    private TiledShapefileOpImage(ImageHeader imageHeader, Map configuration, VirtualDir imageDir,
                                  String filename) throws
                                                   IOException {
        super(imageHeader.getImageLayout(),
              configuration,
              imageHeader.getImageLayout().getSampleModel(null),
              imageHeader.getImageLayout().getMinX(null),
              imageHeader.getImageLayout().getMinY(null),
              imageHeader.getImageLayout().getWidth(null),
              imageHeader.getImageLayout().getHeight(null));
        final File file = new File(imageDir.getBasePath(), filename);
        zipFile = new ZipFile(file);
        if (getTileCache() == null) {
            setTileCache(JAI.getDefaultInstance().getTileCache());
        }
    }

    @Override
    public Raster computeTile(int tileX, int tileY) {
        final Point location = new Point(tileXToX(tileX), tileYToY(tileY));
        final WritableRaster targetRaster = createWritableRaster(sampleModel, location);
        try {
            readRawDataTile(targetRaster, tileX, tileY);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image tile.", e);
        }
        return targetRaster;
    }

    @Override
    public void dispose() {
        try {
            zipFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readRawDataTile(WritableRaster targetRaster, int tileX, int tileY) throws IOException {
        final InputStream inputStream = createImageInputStream(tileX, tileY);
        try {
            final byte[] data = ((DataBufferByte) targetRaster.getDataBuffer()).getData();
            int count = 0;
            int amount = 1024;
            while( count < data.length) {
                if( count + amount > data.length ) {
                    amount = data.length - count;
                }
                count += inputStream.read(data, count, amount);
            }
        } finally {
            inputStream.close();
        }
    }

    private InputStream createImageInputStream(int tileX, int tileY) throws IOException {
        String shapefile = getTilename(tileX, tileY);
        final ZipEntry entry = zipFile.getEntry(shapefile);
        return zipFile.getInputStream(entry);
    }

    static String getTilename(int tileX, int tileY) {
        if (tileX < 0 || tileX > 379 || tileY < 0 || tileY > 179) {
            throw new IllegalArgumentException(
                    "tileX has to be in [0...379], is " + tileX + "; tileY has to be in [0...179], is " + tileY + ".");
        }
        StringBuilder basename = new StringBuilder();

        String horizontalToken;
        String horizonatlInt;
        if (tileX <= 179) {
            horizonatlInt = "" + Math.abs(tileX - 180);
            horizontalToken = "w";
        } else {
            horizonatlInt = "" + Math.abs(tileX - 180);
            horizontalToken = "e";
        }
        if (horizonatlInt.length() == 2) {
            horizonatlInt = "0" + horizonatlInt;
        } else if (horizonatlInt.length() == 1) {
            horizonatlInt = "00" + horizonatlInt;
        }

        String verticalToken;
        String verticalInt;
        if (tileY <= 89) {
            verticalInt = "" + Math.abs(tileY - 89);
            verticalToken = "n";
        } else {
            verticalInt = "" + Math.abs(tileY - 89);
            verticalToken = "s";
        }
        if (verticalInt.length() == 1) {
            verticalInt = "0" + verticalInt;
        }

        basename.append(horizontalToken);
        basename.append(horizonatlInt);
        basename.append(verticalToken);
        basename.append(verticalInt);
        basename.append(".img");

        return basename.toString();
    }
}
