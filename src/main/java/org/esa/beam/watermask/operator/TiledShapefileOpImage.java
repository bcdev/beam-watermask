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
    private String shapefile;

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
            for( int i = 0; i < data.length; i++ ) {
                data[i] = (byte) inputStream.read();
            }
        } finally {
            inputStream.close();
        }
    }

    private InputStream createImageInputStream(int tileX, int tileY) throws IOException {
        final ZipEntry entry = zipFile.getEntry(shapefile);
        return zipFile.getInputStream(entry);
    }

    public void setShapefile(String shapefile) {
        this.shapefile = shapefile;
    }
}
