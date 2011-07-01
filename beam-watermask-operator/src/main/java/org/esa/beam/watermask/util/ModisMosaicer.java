/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
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

import com.bc.ceres.glevel.*;
import com.kenai.jaffl.struct.*;
import org.esa.beam.framework.dataio.*;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.*;
import org.esa.beam.jai.*;
import org.esa.beam.util.*;
import org.esa.beam.watermask.operator.*;

import javax.media.jai.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * @author Thomas Storm
 */
public class ModisMosaicer {

    private static final int MODIS_IMAGE_WIDTH = 155520;
    private static final int MODIS_IMAGE_HEIGHT = 12960;
    private static final int MODIS_TILE_WIDTH = 576;
    private static final int MODIS_TILE_HEIGHT = 480;

    public static void main(String[] args) throws IOException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
        final File[] files = new File("C:\\dev\\projects\\beam-watermask\\MODIS\\south").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".dim");
            }
        });

        Product[] products = new Product[files.length];
        for (int i = 0, filesLength = files.length; i < filesLength; i++) {
            final Product product = ProductIO.readProduct(files[i]);
            products[i] = product;
        }

        int width = MODIS_IMAGE_WIDTH;
        int height = MODIS_IMAGE_HEIGHT;
        final Properties properties = new Properties();
        properties.setProperty("width", String.valueOf(width));
        properties.setProperty("dataType", "0");
        properties.setProperty("height", String.valueOf(height));
        properties.setProperty("tileWidth", String.valueOf(MODIS_TILE_WIDTH));
        properties.setProperty("tileHeight", String.valueOf(MODIS_TILE_HEIGHT));
        final ImageHeader imageHeader = ImageHeader.load(properties, null);
        final TemporaryMODISImage temporaryMODISImage = new TemporaryMODISImage(imageHeader, products);
        final Product product = new Product("MODIS_lw", "lw", MODIS_IMAGE_WIDTH, MODIS_IMAGE_HEIGHT);
        final Band band = product.addBand("lw-mask", ProductData.TYPE_UINT8);
        band.setSourceImage(temporaryMODISImage);
        ProductIO.writeProduct(product, "C:\\temp\\modis_south.dim", "BEAM-DIMAP");
    }

    private static class TemporaryMODISImage extends SourcelessOpImage {

        private final Product[] products;
        private int count = 0;

        public TemporaryMODISImage(ImageHeader imageHeader, Product[] products) {
            super(imageHeader.getImageLayout(),
                    null,
                    ImageUtils.createSingleBandedSampleModel(DataBuffer.TYPE_BYTE,
                            imageHeader.getImageLayout().getSampleModel(null).getWidth(),
                            imageHeader.getImageLayout().getSampleModel(null).getHeight()),
                    imageHeader.getImageLayout().getMinX(null),
                    imageHeader.getImageLayout().getMinY(null),
                    imageHeader.getImageLayout().getWidth(null),
                    imageHeader.getImageLayout().getHeight(null));
            this.products = products;
            setTileCache(JAI.createTileCache(50L * 1024 * 1024));
        }

        @Override
        public Raster computeTile(int tileX, int tileY) {
            count++;
            final int numTiles = getNumXTiles() * getNumYTiles();
            System.out.println("Writing tile '" + tileX + ", " + tileY + "', which is tile " + count + "/" + numTiles + ".");
            Point location = new Point(tileXToX(tileX), tileYToY(tileY));
            WritableRaster dest = createWritableRaster(getSampleModel(), location);
            final PixelPos pixelPos = new PixelPos();

            for (int x = dest.getMinX(); x < dest.getMinX() + dest.getWidth(); x++) {
                for (int y = dest.getMinY(); y < dest.getMinY() + dest.getHeight(); y++) {

                    if (y > 10860 || (y > 10474 && x > 154129) || (y > 10460 && x < 1436)) {
                        dest.setSample(x, y, 0, WatermaskClassifier.LAND_VALUE);
                        continue;
                    }

                    int yOffset = 0;
                    if(y == 4286 || y == 8601) {
                        yOffset = -1;
                    }

                    int xOffset = 0;
                    if(x == 77758 || x == 77759 || x == 77760) {
                        xOffset = -1;
                    }

                    dest.setSample(x, y, 0, WatermaskClassifier.WATER_VALUE);
                    final GeoPos geoPos = getGeoPos(x + xOffset, y + yOffset);
                    final Product[] products = getProducts(geoPos);
                    for (Product product : products) {
                        product.getGeoCoding().getPixelPos(geoPos, pixelPos);
                        final Band band = product.getBand("water_mask");
                        final MultiLevelImage sourceImage = band.getSourceImage();
                        final Raster tile = sourceImage.getTile(sourceImage.XToTileX((int) pixelPos.x), sourceImage.YToTileY((int) pixelPos.y));
                        final int sample = tile.getSample((int) pixelPos.x, (int) pixelPos.y, 0);
                        if (sample != band.getNoDataValue()) {
                            dest.setSample(x, y, 0, sample);
                            break;
                        }
                    }
                }
            }

            return dest;
        }

        private GeoPos getGeoPos(int x, int y) {
            final double pixelSizeX = 360.0 / MODIS_IMAGE_WIDTH;
            final double pixelSizeY = -30.0 / MODIS_IMAGE_HEIGHT;
            double lon = -180.0 + x * pixelSizeX;
            double lat = -60.0 + y * pixelSizeY;
            return new GeoPos((float) lat, (float) lon);
        }

        private Product[] getProducts(GeoPos geoPos) {
            final List<Product> result = new ArrayList<Product>();
            for (Product product : products) {
                final PixelPos pixelPos = product.getGeoCoding().getPixelPos(geoPos, null);
                if (pixelPos.isValid() &&
                        pixelPos.x > 0 &&
                        pixelPos.x < product.getSceneRasterWidth() &&
                        pixelPos.y > 0 &&
                        pixelPos.y < product.getSceneRasterHeight()) {
                    result.add(product);
                }
            }
            return result.toArray(new Product[result.size()]);
        }

    }
}
