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
import java.text.MessageFormat;

/**
 * The watermask operator is a GPF-Operator. It takes the geographic bounds of the input product and creates a new
 * product with the same bounds. The output product contains a single band, which is a land/water fraction mask.
 * For each pixel, it contains the fraction of water; a value of 0.0 indicates land, a value of 100.0 indicates water,
 * and every value in between indicates a mixed pixel.
 * <br/>
 * The water mask is based on data given by SRTM-shapefiles between 60° north and 60° south, and by the GlobCover world
 * map above 60° north.
 * Since the base data may exhibit a higher resolution than the input product, a subsampling &ge;1 may be specified;
 * therefore, mixed pixels may occur.
 *
 * @author Thomas Storm
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "LandWaterMask",
                  version = "1.3",
                  internal = false,
                  authors = "Thomas Storm",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Operator creating a target product with a single band containing a land/water-mask," +
                                " which is based on SRTM-shapefiles (between 60° north and 60° south) and the " +
                                "GlobCover world map (above 60° north) and therefore very accurate.")
public class WatermaskOp extends Operator {

    @SourceProduct(alias = "source", description = "The Product the land/water-mask shall be computed for.",
                   label = "Name")
    private Product sourceProduct;

    @Parameter(description = "Specifies on which resolution the water mask shall be based.", unit = "m/pixel",
               label = "Resolution", defaultValue = "50", valueSet = {"50", "150"})
    private int resolution;

    @Parameter(description = "Specifies the factor between the resolution of the source product and the watermask in " +
                             "x direction. A value of '1' means no subsampling at all.",
               label = "Subsampling factor x", defaultValue = "1", notNull = true)
    private int subSamplingFactorX;

    @Parameter(description = "Specifies the factor between the resolution of the source product and the watermask in" +
                             "y direction. A value of '1' means no subsampling at all.",
               label = "Subsampling factor y", defaultValue = "1", notNull = true)
    private int subSamplingFactorY;

    @TargetProduct
    private Product targetProduct;
    private WatermaskClassifier classifier;

    @Override
    public void initialize() throws OperatorException {
        validateParameter();
        validateSourceProduct();
        initTargetProduct();
        try {
            classifier = new WatermaskClassifier(resolution, subSamplingFactorX, subSamplingFactorY);
        } catch (IOException e) {
            throw new OperatorException("Error creating class WatermaskClassifier.", e);
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();
        try {
            final GeoCoding geoCoding = sourceProduct.getGeoCoding();
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final byte waterFraction = classifier.getWaterMaskFraction(geoCoding, x, y);
                    targetTile.setSample(x, y, waterFraction);
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
        if (subSamplingFactorX < 1) {
            String msgPattern = "Subsampling factor needs to be greater than or equal to 1; was: ''{0}''.";
            String message = MessageFormat.format(msgPattern, subSamplingFactorX);
            throw new OperatorException(message);
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
        final Band band = targetProduct.addBand("land_water_fraction", ProductData.TYPE_INT8);
        band.setNoDataValue(WatermaskClassifier.INVALID_VALUE);
        band.setNoDataValueUsed(true);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WatermaskOp.class);
        }
    }
}
