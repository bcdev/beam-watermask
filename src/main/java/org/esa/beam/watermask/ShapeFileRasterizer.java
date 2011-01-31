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

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.MapContext;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Fill;
import org.geotools.styling.PolygonSymbolizer;
import org.geotools.styling.Rule;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Responsible for transferring shapefiles containing a land/water-mask into a rasterized image.
 *
 * @author Thomas Storm
 */
public class ShapeFileRasterizer {

    private static final String ZIPFILE_NAME = "images.zip";
    private final Set<File> tempFiles;
    private final File targetDir;

    private static final int SIDE_LENGTH = 1024;
    private static final Point IMAGE_SIZE = new Point(SIDE_LENGTH, SIDE_LENGTH);

    public ShapeFileRasterizer(File targetDir) {
        tempFiles = new HashSet<File>();
        this.targetDir = targetDir;
    }

    public static void main(String[] args) throws IOException {
        final File resourceDir = new File(args[0]);
        final File targetDir = new File(args[1]);
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Error: two arguments needed. Argument 1: directory containing shapefiles. Argument 2: target directory.");
        }
        final ShapeFileRasterizer rasterizer = new ShapeFileRasterizer(targetDir);
        rasterizer.rasterizeShapefiles(resourceDir);
    }

    void rasterizeShapefiles(File dir) throws IOException {
        File[] subdirs = dir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        File[] shapeFiles = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".zip");
            }
        });
        if (shapeFiles != null) {
            for (int i = 0, shapeFilesLength = shapeFiles.length; i < shapeFilesLength; i++) {
                File shapeFile = shapeFiles[i];
                ZipFile zipFile = new ZipFile(shapeFile);
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                List<File> tempShapeFiles;
                try {
                    tempShapeFiles = generateTempFiles(zipFile, entries);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Error generating temp files from shapefile '" + shapeFile.getAbsolutePath() + "'.", e);
                }
                for (File file : tempShapeFiles) {
                    if (file.getName().endsWith("shp")) {
                        final BufferedImage image = createImage(file);
                        writeToFile(image, shapeFile.getName());
                        deleteTempFiles();
                    }
                }
            }
        }
        if (subdirs != null) {
            for (File subDir : subdirs) {
                rasterizeShapefiles(subDir);
            }
        }
        zipFiles();
    }

    BufferedImage createImage(File shapeFile) throws IOException {
        CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;
        MapContext context = new DefaultMapContext(crs);

        URL url = shapeFile.toURI().toURL();
        final FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = getFeatureSource(url);
        context.addLayer(featureSource, createPolygonStyle());

        int width = IMAGE_SIZE.x;
        int height = IMAGE_SIZE.y;

        BufferedImage landMaskImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = landMaskImage.createGraphics();

        int lonMin = Integer.parseInt(shapeFile.getName().substring(1, 4));
        int lonMax = lonMin + 1;
        int latMin = Integer.parseInt(shapeFile.getName().substring(5, 7));
        int latMax = latMin + 1;

        StreamingRenderer renderer = new StreamingRenderer();
        renderer.setContext(context);
        renderer.paint(graphics, new Rectangle(0, 0, width, height),
                       new ReferencedEnvelope(lonMin, lonMax, latMin, latMax, crs));

        tempFiles.add(new File(getFilenameWithoutExtension(shapeFile.getName()) + ".fix"));
        tempFiles.add(new File(getFilenameWithoutExtension(shapeFile.getName()) + ".qix"));
        return landMaskImage;
    }

    private void writeToFile(BufferedImage image, String name) throws IOException {
        StringBuilder fileName = new StringBuilder(getFilenameWithoutExtension(name));
        fileName.append(".img");
        File outputFile = new File(targetDir.getAbsolutePath() + File.separatorChar + fileName.toString());

        byte[] data = ((DataBufferByte) image.getData().getDataBuffer()).getData();
        FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        System.out.println("Writing file '" + outputFile.getAbsolutePath() + "'.");

        try {
            fileOutputStream.write(data);
        } catch (IOException e) {
            throw new IllegalStateException("Error writing file '" + outputFile.getAbsolutePath() + "'.", e);
        } finally {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void zipFiles() throws IOException {
        byte[] buffer = new byte[1];
        final String outFilename = targetDir.getAbsolutePath() + File.separatorChar + WatermaskClassifier.ZIP_FILENAME;
        ZipOutputStream writer = new ZipOutputStream(new FileOutputStream(outFilename));
        final ArrayList<InputStream> inputStreams = new ArrayList<InputStream>();
        try {
            final File[] files = targetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !ZIPFILE_NAME.equals(name);
                }
            });
            for (File file : files) {
                FileInputStream reader = new FileInputStream(file.getAbsolutePath());
                inputStreams.add(reader);
                writer.putNextEntry(new ZipEntry(file.getName()));
                while (reader.read(buffer) != -1) {
                    writer.write(buffer);
                }
                writer.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error generating zip file '" + outFilename + "'.", e);
        } finally {
            for (InputStream reader : inputStreams) {
                reader.close();
            }
            writer.close();
        }
    }

    private List<File> generateTempFiles(ZipFile zipFile, Enumeration<? extends ZipEntry> entries) throws
                                                                                                   IOException {
        List<File> files = new ArrayList<File>();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            File file = readIntoTempFile(zipFile, entry);
            files.add(file);
        }
        return files;
    }

    private File readIntoTempFile(ZipFile zipFile, ZipEntry entry) throws IOException {
        File file = new File(entry.getName());
        tempFiles.add(file);
        byte[] buffer = new byte[1];
        FileOutputStream writer = new FileOutputStream(file);
        final InputStream reader = zipFile.getInputStream(entry);
        while (reader.read(buffer) != -1) {
            writer.write(buffer);
        }
        writer.close();
        return file;
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    private void deleteTempFiles() {
        for (File tempFile : tempFiles) {
            tempFile.delete();
        }
        tempFiles.clear();
    }

    private static String getFilenameWithoutExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i > 0 && i < fileName.length() - 1) {
            return fileName.substring(0, i);
        }
        return fileName;
    }

    private FeatureSource<SimpleFeatureType, SimpleFeature> getFeatureSource(URL url) throws IOException {
        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put(ShapefileDataStoreFactory.URLP.key, url);
        parameterMap.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.TRUE);
        DataStore shapefileStore = DataStoreFinder.getDataStore(parameterMap);
        String typeName = shapefileStore.getTypeNames()[0]; // Shape files do only have one type name
        FeatureSource<SimpleFeatureType, SimpleFeature> featureSource;
        featureSource = shapefileStore.getFeatureSource(typeName);
        return featureSource;
    }

    private Style createPolygonStyle() {
        StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory(null);
        FilterFactory filterFactory = CommonFactoryFinder.getFilterFactory(null);

        PolygonSymbolizer symbolizer = styleFactory.createPolygonSymbolizer();
        org.geotools.styling.Stroke stroke = styleFactory.createStroke(
                filterFactory.literal("#FFFFFF"),
                filterFactory.literal(0.0)
        );
        symbolizer.setStroke(stroke);
        Fill fill = styleFactory.createFill(
                filterFactory.literal("#FFFFFF"),
                filterFactory.literal(1.0)
        );
        symbolizer.setFill(fill);

        Rule rule = styleFactory.createRule();
        rule.symbolizers().add(symbolizer);

        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle();
        fts.rules().add(rule);

        Style style = styleFactory.createStyle();
        style.featureTypeStyles().add(fts);

        return style;
    }
}