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
import org.esa.beam.framework.gpf.annotations.Parameter;
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

    @Parameter(alias = "Resolution", description = "Resolution in m/pixel", defaultValue = "50",
               valueSet = {"50", "150"})
    private int resolution;

    @Parameter(description = "Automatically fill pixels where no shapefile exists (experimental code)",
               defaultValue = "false", valueSet = {"true", "false"})
    private boolean fill;

    @SourceProduct(alias = "Name", description = "The Product the land/water-mask shall be computed for.")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    private WatermaskClassifier classifier;

    @Override
    public void initialize() throws OperatorException {
        initTargetProduct();
        try {
            classifier = new WatermaskClassifier(resolution, fill);
        } catch (IOException e) {
            throw new OperatorException("Error creating Watermask Classifier.", e);
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        System.out.println("WatermaskOp.computeTile: " + targetTile.getRectangle().toString());
        final Rectangle rectangle = targetTile.getRectangle();
        GeoPos geoPos = new GeoPos();
        try {
            final PixelPos pixelPos = new PixelPos();
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    pixelPos.setLocation(x, y);
                    targetBand.getGeoCoding().getGeoPos(pixelPos, geoPos);
                    targetTile.setSample(x, y, classifier.getWaterMaskSample(geoPos.lat, geoPos.lon));
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Error computing tile '" + targetTile.getRectangle().toString() + "'.", e);
        }
    }

    private void initTargetProduct() {
        if (sourceProduct.getGeoCoding() == null) {
            throw new OperatorException("Input product is not geo-referenced.");
        }
        targetProduct = new Product("LW-Mask", ProductData.TYPESTRING_UINT8, sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());
        final Band lwBand = targetProduct.addBand("Land-Water-Mask", ProductData.TYPE_UINT8);
        lwBand.setNoDataValue(2);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WatermaskOp.class);
        }
    }
}
