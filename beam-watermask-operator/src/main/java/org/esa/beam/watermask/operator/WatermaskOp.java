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
import org.esa.beam.framework.datamodel.GeoCoding;
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
 * SRTM shape files.
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

    @SourceProduct(alias = "source", description = "The Product the land/water-mask shall be computed for.",
                   label = "Name")
    private Product sourceProduct;

    @Parameter(description = "Specifies on which resolution the water mask shall be based.", unit = "m/pixel",
               label = "Resolution", defaultValue = "50", valueSet = {"50", "150"})
    private int resolution;

    @Parameter(description = "Specifies the factor between the resolution of the source product and the watermask.",
               label = "Subsampling factor", defaultValue = "10", notNull = true)
    private int subSamplingFactor;

    @TargetProduct
    private Product targetProduct;
    private WatermaskClassifier classifier;

    @Override
    public void initialize() throws OperatorException {
        validateParameter();
        validateSourceProduct();
        initTargetProduct();
        try {
            classifier = new WatermaskClassifier(resolution);
        } catch (IOException e) {
            throw new OperatorException("Error creating class WatermaskClassifier.", e);
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();
        GeoPos geoPos = new GeoPos();
        try {
            final PixelPos pixelPos = new PixelPos();
            final GeoCoding geoCoding = targetBand.getGeoCoding();
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    int waterMaskSample = 0;
                    for (int sx = 0; sx < subSamplingFactor; sx++) {
                        for (int sy = 0; sy < subSamplingFactor; sy++) {
                            pixelPos.setLocation(x + sx * (1.0/subSamplingFactor), y + sy * (1.0/subSamplingFactor));
                            geoCoding.getGeoPos(pixelPos, geoPos);
                            final int sample = classifier.getWaterMaskSample(geoPos.lat, geoPos.lon);
                            if (sample != WatermaskClassifier.INVALID_VALUE) {
                                waterMaskSample += sample;
                            }
                        }
                    }
                   targetTile.setSample(x, y, 100 * waterMaskSample / (subSamplingFactor * subSamplingFactor));
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Error computing tile '" + targetTile.getRectangle().toString() + "'.", e);
        }
    }

    private void validateParameter() {
        if (resolution != WatermaskClassifier.RESOLUTION_50 && resolution != WatermaskClassifier.RESOLUTION_150) {
            throw new OperatorException(String.format("Resolution needs to be either %d or %d.",
                                                      WatermaskClassifier.RESOLUTION_50,
                                                      WatermaskClassifier.RESOLUTION_150));
        }
    }

    private void validateSourceProduct() {
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding == null) {
            throw new OperatorException("The source product must be geo-coded.");
        }
        if (!geoCoding.canGetGeoPos()) {
            throw new OperatorException("The geo-coding of the source product can not be used.\n" +
                                        "It does not provide the geo-position for a pixel position.");
        }
    }

    private void initTargetProduct() {
        targetProduct = new Product("LW-Mask", ProductData.TYPESTRING_UINT8, sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());
        final Band band = targetProduct.addBand("land_water_fraction", ProductData.TYPE_FLOAT32);
        band.setNoDataValue(WatermaskClassifier.INVALID_VALUE);
        band.setNoDataValueUsed(true);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        if (band.getGeoCoding() == null) {
            throw new OperatorException("Geo-reference information could not be copied.");
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WatermaskOp.class);
        }
    }
}
