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

import org.esa.beam.jai.ImageHeader;
import org.esa.beam.util.ImageUtils;
import org.esa.beam.util.jai.SingleBandedSampleModel;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.SourcelessOpImage;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Thomas Storm
 */
public class GCOpImage extends SourcelessOpImage {

    private final ZipFile zipFile;

    private GCOpImage(ImageHeader imageHeader, File zipFile) throws IOException {
        super(imageHeader.getImageLayout(),
              null,
              ImageUtils.createSingleBandedSampleModel(DataBuffer.TYPE_BYTE,
                                                       imageHeader.getImageLayout().getSampleModel(null).getWidth(),
                                                       imageHeader.getImageLayout().getSampleModel(null).getHeight()),
              imageHeader.getImageLayout().getMinX(null),
              imageHeader.getImageLayout().getMinY(null),
              imageHeader.getImageLayout().getWidth(null),
              imageHeader.getImageLayout().getHeight(null));
        this.zipFile = new ZipFile(zipFile);
        // this image uses its own tile cache in order not to disturb the GPF tile cache.
        setTileCache(JAI.createTileCache(50L * 1024 * 1024));
    }

    public static GCOpImage create(Properties properties, File zipFile) throws IOException {
        final ImageHeader imageHeader = ImageHeader.load(properties, null);
        return new GCOpImage(imageHeader, zipFile);
    }

    @Override
    public Raster computeTile(int tileX, int tileY) {
        Raster raster;
        try {
            raster = computeRawRaster(tileX, tileY);
        } catch (IOException e) {
            throw new RuntimeException(MessageFormat.format("Failed to read image tile ''{0} | {1}''.", tileX, tileY), e);
        }
        return raster;

    }

    private Raster computeRawRaster(int tileX, int tileY) throws IOException {
        String fileName = getFileName(tileX, tileY);
        ZipEntry zipEntry = zipFile.getEntry(fileName);
        final Point location = new Point(tileXToX(tileX), tileYToY(tileY));
        final WritableRaster targetRaster = createWritableRaster(new SingleBandedSampleModel(DataBuffer.TYPE_BYTE, 576, 491), location);

        InputStream inputStream = null;
        try {
            inputStream = zipFile.getInputStream(zipEntry);
            BufferedImage image = ImageIO.read(inputStream);
            Raster imageData = image.getData();
            for (int x = 0; x < imageData.getWidth(); x++) {
                for (int y = 0; y < imageData.getHeight(); y++) {
                    byte sample = (byte) imageData.getSample(x, y, 0);
                    sample = (byte) Math.abs(sample - 1);
                    targetRaster.setSample(tileXToX(tileX) + x, tileYToY(tileY) + y, 0, sample);
                }
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return targetRaster;
    }

    String getFileName(int tileX, int tileY) {
        return String.format("%d-%d.png", tileX, tileY);
    }
}
