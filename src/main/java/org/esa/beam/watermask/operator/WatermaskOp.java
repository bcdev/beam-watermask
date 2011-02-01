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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.io.IOException;

/**
 * GPF-Operator responsible for creating a product, which contains a single band: a land/water-mask based on
 * SRTM-shapefiles.
 *
 * @author Thomas Storm
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "LandWaterMask",
                  version = "1.0",
                  internal = false,
                  authors = "Thomas Storm",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Land/Water-mask operator.")
public class WatermaskOp extends Operator {

    private final int SIDE_LENGTH = 1024;

    @SourceProduct(description = "The Product the land/water-mask shall be computed for.")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    private WatermaskClassifier classifier;

    @Override
    public void initialize() throws OperatorException {
        final int width = sourceProduct.getSceneRasterWidth();
        final int height = sourceProduct.getSceneRasterHeight();
        targetProduct = new Product("LW-Mask", ProductData.TYPESTRING_UINT8, width, height);
        targetProduct.addBand("Land-Water-Mask", ProductData.TYPE_UINT8);
        try {
            classifier = new WatermaskClassifier(SIDE_LENGTH);
        } catch (IOException e) {
            throw new OperatorException("Error creating Watermask Classifier.", e);
        }
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();
        int amount = rectangle.x * rectangle.y;
        pm.beginTask("Creating land/water-mask...", amount);
        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                final PixelPos pixelPos = new PixelPos(x, y);
                final GeoPos geoPos = targetBand.getGeoCoding().getGeoPos(pixelPos, null);
                byte water = -1;
                try {
                    water = (byte) (classifier.isWater(geoPos.lat, geoPos.lon) ? 1 : 0);
                } catch (Exception e) {
                    // TODO - don't set value to -1 but to values of adjacent tiles
//                    if (!e.getMessage().startsWith("No image found")) {
//                        System.err.println("WARNING: " + e.getMessage());
//                    }
                }
                targetTile.setSample(x, y, water);
                pm.worked(1);
            }
        }
        pm.done();
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WatermaskOp.class);
        }
    }
}
