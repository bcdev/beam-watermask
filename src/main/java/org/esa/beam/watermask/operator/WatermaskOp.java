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
                  description = "Operator creating a target product with a single band containing a land/water-mask," +
                                " which is based on SRTM-shapefiles and therefore very accurate.")
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
        System.out.println("WatermaskOp.computeTile: " + targetTile.getRectangle().toString());
        final Rectangle rectangle = targetTile.getRectangle();
        GeoPos geoPos = null;
        try {
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    final PixelPos pixelPos = new PixelPos(x, y);
                    geoPos = targetBand.getGeoCoding().getGeoPos(pixelPos, null);
                    int water = 2;
                    if (classifier.shapeFileExists(new WatermaskClassifier.GeoPos(geoPos.lat, geoPos.lon))) {
                        water = classifier.getWaterMaskSample(geoPos.lat, geoPos.lon);
//                    } else {
//                        water = getTypeOfAdjacentTiles(geoPos);
                    }
                    targetTile.setSample(x, y, water);
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Error computing tile '" + targetTile.getRectangle().toString() + "'.", e);
        }
    }

    private byte getTypeOfAdjacentTiles(GeoPos geoPos) {
        float leftLon = (float) ((int)geoPos.lon - 0.0001);
        float rightLon = (float) ((int)geoPos.lon + 0.0001);
        float topLat = (float) ((int)geoPos.lat + 0.0001);
        float bottomLat = (float) ((int)geoPos.lat - 0.0001);

        int result = 0;
        try {
            result += classifier.getWaterMaskSample(geoPos.lat, leftLon);
            result += classifier.getWaterMaskSample(geoPos.lat, rightLon);
            result += classifier.getWaterMaskSample(topLat, geoPos.lon);
            result += classifier.getWaterMaskSample(bottomLat, geoPos.lon);
        } catch (IOException e) {
            // ok, handled by following 'if'-statement
        }

        if (result == 4) {
            return 1;
        } else if (result == 0) {
            return 0;
        } else {
//            if no unambiguous result is returned, take the value of the left adjacent tile
            return getTypeOfAdjacentTiles(new GeoPos(geoPos.lat, leftLon));
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WatermaskOp.class);
        }
    }
}
