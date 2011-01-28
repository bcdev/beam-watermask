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

    Set<File> filesToDelete = new HashSet<File>();
    private final File targetDir;

    public ShapeFileRasterizer(File targetDir) {
        this.targetDir = targetDir;
    }

    public static void main(String[] args) throws IOException {
        final File resourceDir = new File(args[0]);
        final File targetDir = new File(args[1]);
        if (args.length != 2) {
            throw new IllegalArgumentException("Directory containing shapefiles and target directory are needed.");
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
                List<File> tempFiles = generateTempFiles(zipFile, entries);
                for (File file : tempFiles) {
                    if (file.getName().endsWith("shp")) {
                        final BufferedImage image = createImage(file);
                        writeToFile(image, shapeFile.getName());
                        deleteFilesToDelete();
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

    BufferedImage createImage(File file) throws IOException {
        CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;
        MapContext context = new DefaultMapContext(crs);

        URL url = file.toURI().toURL();
        final FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = getFeatureSource(url);
        context.addLayer(featureSource, createPolygonStyle());

        Point p = WatermaskClassifier.computeImagePixelCount();
        int width = p.x;
        int height = p.y;

        BufferedImage landMaskImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = landMaskImage.createGraphics();

        int lonMin = Integer.parseInt(file.getName().substring(1, 4));
        int lonMax = lonMin + 1;
        int latMin = Integer.parseInt(file.getName().substring(5, 7));
        int latMax = latMin + 1;

        StreamingRenderer renderer = new StreamingRenderer();
        renderer.setContext(context);
        renderer.paint(graphics, new Rectangle(0, 0, width, height),
                       new ReferencedEnvelope(lonMin, lonMax, latMin, latMax, crs));

        filesToDelete.add(new File(getFilenameWithoutExtension(file.getName()) + ".fix"));
        filesToDelete.add(new File(getFilenameWithoutExtension(file.getName()) + ".qix"));
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
            for (byte chunk : data) {
                fileOutputStream.write(chunk);
            }
        } catch (IOException e) {
            // TODO - handle
        } finally {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void zipFiles() throws IOException {
        byte[] buf = new byte[1];
        final String outFilename = targetDir.getAbsolutePath() + File.separatorChar + WatermaskClassifier.ZIP_FILENAME;
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename));
        try {
            final File[] files = targetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !"images.zip".equals(name);
                }
            });
            for (File file : files) {
                FileInputStream in = new FileInputStream(file.getAbsolutePath());
                out.putNextEntry(new ZipEntry(file.getName()));
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.closeEntry();
                in.close();
            }
        } catch (IOException e) {
            // TODO - handle
        } finally {
            out.close();
        }
    }

    private List<File> generateTempFiles(ZipFile zipFile, Enumeration<? extends ZipEntry> entries) throws
                                                                                                   IOException {
        List<File> tempFiles = new ArrayList<File>();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            File file = readIntoTempFile(zipFile, entry);
            tempFiles.add(file);
        }
        return tempFiles;
    }

    private File readIntoTempFile(ZipFile zipFile, ZipEntry entry) throws IOException {
        File file = new File(entry.getName());
        filesToDelete.add(file);
        byte[] buffer = new byte[1];
        FileOutputStream fos = new FileOutputStream(file);
        final InputStream inputStream = zipFile.getInputStream(entry);
        while (inputStream.read(buffer) != -1) {
            fos.write(buffer);
        }
        fos.close();
        return file;
    }

    private void deleteFilesToDelete() {
        for (File tempFile : filesToDelete) {
            tempFile.delete();
        }
        filesToDelete.clear();
    }

    static String getFilenameWithoutExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i > 0 && i < fileName.length() - 1) {
            return fileName.substring(0, i);
        }
        return fileName;
    }

    private FeatureSource<SimpleFeatureType, SimpleFeature> getFeatureSource(URL url) throws IOException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(ShapefileDataStoreFactory.URLP.key, url);
        map.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.TRUE);
        DataStore shapefileStore = DataStoreFinder.getDataStore(map);
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