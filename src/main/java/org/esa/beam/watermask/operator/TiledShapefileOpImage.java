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

import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.SourcelessOpImage;
import java.awt.Point;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Responsible for holding the data in tiles.
 *
 * @author Thomas Storm
 */
public class TiledShapefileOpImage extends SourcelessOpImage {

    private VirtualDir imageDir;
    private ImageInputStreamFactory inputStreamFactory;
    private boolean disposed;

    public static TiledShapefileOpImage create(VirtualDir imageDir, Properties defaultImageProperties) throws IOException {
        final ImageHeader imageHeader = ImageHeader.load(new File(imageDir.getBasePath()), defaultImageProperties);
        return new TiledShapefileOpImage(imageHeader, null, imageDir);
    }


    private TiledShapefileOpImage(ImageHeader imageHeader, Map configuration, VirtualDir imageDir) {
        super(imageHeader.getImageLayout(),
              configuration,
              imageHeader.getImageLayout().getSampleModel(null),
              imageHeader.getImageLayout().getMinX(null),
              imageHeader.getImageLayout().getMinY(null),
              imageHeader.getImageLayout().getWidth(null),
              imageHeader.getImageLayout().getHeight(null));
        this.imageDir = imageDir;
        inputStreamFactory = new RawZipImageInputStreamFactory();
        if (getTileCache() == null) {
            setTileCache(JAI.getDefaultInstance().getTileCache());
        }
    }

    @Override
    public Raster computeTile(int tileX, int tileY) {
        final Point location = new Point(tileXToX(tileX), tileYToY(tileY));
        final WritableRaster targetRaster = createWritableRaster(sampleModel, location);
        try {
            readRawDataTile(tileX, tileY, targetRaster);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image tile.", e);
        }
        return targetRaster;
    }

    private void readRawDataTile(int tileX, int tileY, WritableRaster targetRaster) throws IOException {
        final ImageInputStream imageInputStream = inputStreamFactory.createImageInputStream(tileX, tileY);
        try {
            readRawDataTile(imageInputStream, targetRaster);
        } finally {
            imageInputStream.close();
        }
    }

    /**
     * Reads the data buffer of the given raster from the given image input stream.
     *
     * @param raster The raster.
     * @param stream The image input stream.
     *
     * @throws java.io.IOException      if an I/O error occurs
     * @throws IllegalArgumentException if the {@code raster}'s data arrays cannot be retrieved
     * @throws NullPointerException     if {@code raster} or {@code stream} is null
     */
    public static void readRawDataTile(ImageInputStream stream, WritableRaster raster) throws IOException {
        final Object dataObject = getDataObject(raster);
        if (dataObject instanceof byte[]) {
            final byte[] data = (byte[]) dataObject;
            stream.readFully(data, 0, data.length);
        } else if (dataObject instanceof short[]) {
            final short[] data = (short[]) dataObject;
            stream.readFully(data, 0, data.length);
        } else if (dataObject instanceof int[]) {
            final int[] data = (int[]) dataObject;
            stream.readFully(data, 0, data.length);
        } else if (dataObject instanceof float[]) {
            final float[] data = (float[]) dataObject;
            stream.readFully(data, 0, data.length);
        } else if (dataObject instanceof double[]) {
            final double[] data = (double[]) dataObject;
            stream.readFully(data, 0, data.length);
        } else {
            throw new IllegalArgumentException(
                    "raster: Unexpected type returned by raster.getDataBuffer().getData(): " + dataObject);
        }
    }

    /**
     * Gets the data object from the data buffer of the given raster.
     * The data object which will always be of a primitive array type.
     *
     * @param raster The raster.
     *
     * @return The data array.
     *
     * @throws IllegalArgumentException if the {@code raster}'s data arrays cannot be retrieved
     * @throws NullPointerException     if {@code raster} is null
     */
    public static Object getDataObject(Raster raster) {
        final DataBuffer dataBuffer = raster.getDataBuffer();
        final Object arrayObject;
        if (dataBuffer instanceof DataBufferByte) {
            arrayObject = ((DataBufferByte) dataBuffer).getData();
        } else if (dataBuffer instanceof DataBufferShort) {
            arrayObject = ((DataBufferShort) dataBuffer).getData();
        } else if (dataBuffer instanceof DataBufferUShort) {
            arrayObject = ((DataBufferUShort) dataBuffer).getData();
        } else if (dataBuffer instanceof DataBufferInt) {
            arrayObject = ((DataBufferInt) dataBuffer).getData();
        } else if (dataBuffer instanceof DataBufferFloat) {
            arrayObject = ((DataBufferFloat) dataBuffer).getData();
        } else if (dataBuffer instanceof DataBufferDouble) {
            arrayObject = ((DataBufferDouble) dataBuffer).getData();
        } else if (dataBuffer instanceof javax.media.jai.DataBufferFloat) {
            arrayObject = ((javax.media.jai.DataBufferFloat) dataBuffer).getData();
        } else if (dataBuffer instanceof javax.media.jai.DataBufferDouble) {
            arrayObject = ((javax.media.jai.DataBufferDouble) dataBuffer).getData();
        } else {
            try {
                final Method method = dataBuffer.getClass().getMethod("getData");
                arrayObject = method.invoke(dataBuffer);
            } catch (Throwable t) {
                throw new IllegalArgumentException("raster: Failed to invoke raster.getDataBuffer().getData().", t);
            }
        }
        if (arrayObject == null) {
            throw new IllegalArgumentException("raster: raster.getDataBuffer().getData() returned null.");
        }
        return arrayObject;
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        inputStreamFactory = null;
        super.dispose();
    }

    static String getTileBasename(int tileX, int tileY) {
        if (tileX < 0 || tileX > 379 || tileY < 0 || tileY > 179) {
            throw new IllegalArgumentException(
                    "tileX has to be between 0 and 379, is " + tileX + "; tileY has to lie between 0 and 179, is " + tileY + ".");
        }
        StringBuilder basename = new StringBuilder();

        String horizontalToken;
        String horizonatlInt;
        if (tileX <= 179) {
            horizonatlInt = "" + Math.abs(tileX - 179);
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
            verticalInt = "" + Math.abs(tileY - 90);
            verticalToken = "s";
        }
        if (verticalInt.length() == 1) {
            verticalInt = "0" + verticalInt;
        }

        basename.append(horizontalToken);
        basename.append(horizonatlInt);

        basename.append(verticalToken);
        basename.append(verticalInt);

        return basename.toString();
    }

    private interface ImageInputStreamFactory {

        ImageInputStream createImageInputStream(int tileX, int tileY) throws IOException;
    }

    private class RawZipImageInputStreamFactory implements ImageInputStreamFactory {

        private File tmpDir;

        private RawZipImageInputStreamFactory() {
            tmpDir = new File(System.getProperty("java.io.tmpdir", ".temp"));
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            // System.out.println("TiledFileOpImage: Using temporary directory '" + tmpDir + "'");
        }

        public ImageInputStream createImageInputStream(int tileX, int tileY) throws IOException {
            final String entryName = getTileBasename(tileX, tileY) + ".img";
            final File file = new File(imageDir.getBasePath(), "images.zip");
            final ZipFile zipFile = new ZipFile(file);
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry zipEntry = entries.nextElement();
                if (zipEntry.getName().startsWith(entryName)) {
                    final InputStream inputStream = zipFile.getInputStream(zipEntry);
                    return new FileCacheImageInputStream(inputStream, tmpDir);
                }
            }
            throw new IOException("No tile for coordinates " + tileX + ", " + tileY + ".");
        }
    }
}
