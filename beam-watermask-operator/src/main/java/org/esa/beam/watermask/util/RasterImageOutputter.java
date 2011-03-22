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

import org.esa.beam.util.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * TODO fill out or delete
 *
 * @author Thomas Storm
 */
public class RasterImageOutputter {

    private static final int TILE_WIDTH = ShapeFileRasterizer.computeSideLength(50);

    public static void main(String[] args) throws IOException {

        final File file = new File(args[0]);
        if (file.isDirectory()) {
            final File[] imgFiles = file.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".img");
                }
            });
            final ExecutorService executorService = Executors.newFixedThreadPool(6);
            for (File imgFile : imgFiles) {
                final File outputFile = FileUtils.exchangeExtension(imgFile, ".png");
                if (!outputFile.exists()) {
                    executorService.submit(new ImageWriterRunnable(imgFile, outputFile));
                }
            }
            while (!executorService.isTerminated()) {
                try {
                    executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            executorService.shutdown();
        } else {
            final InputStream inputStream;
            if (args.length == 2) {
                ZipFile zipFile = new ZipFile(file);
                String shapefile = args[1];
                final ZipEntry entry = zipFile.getEntry(shapefile);
                inputStream = zipFile.getInputStream(entry);
            } else {
                inputStream = new FileInputStream(file);
            }
            writeImage(inputStream, new File(args[args.length - 1]));
        }

        System.exit(0);
    }

    private static void writeImage(InputStream inputStream, File outputFile) throws IOException {
        WritableRaster targetRaster = Raster.createPackedRaster(0, TILE_WIDTH, TILE_WIDTH, 1, 1,
                                                                new Point(0, 0));

        final byte[] data = ((DataBufferByte) targetRaster.getDataBuffer()).getData();
        inputStream.read(data);
        final BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_WIDTH, BufferedImage.TYPE_BYTE_BINARY);
        image.setData(targetRaster);
        ImageIO.write(image, "png", outputFile);
    }

    private static class ImageWriterRunnable implements Runnable {

        private File inputFile;
        private File outputFile;

        private ImageWriterRunnable(File inputFile, File outputFile) {

            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }

        @Override
        public void run() {
            InputStream fileInputStream = null;
            try {
                fileInputStream = new FileInputStream(inputFile);
                writeImage(fileInputStream, outputFile);
                System.out.printf("Written: %s%n", outputFile);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}