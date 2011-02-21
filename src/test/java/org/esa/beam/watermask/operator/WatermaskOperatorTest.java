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

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Before;
import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.awt.image.DataBufferByte;
import java.util.HashMap;

import static junit.framework.Assert.*;

/**
 * @author Thomas Storm
 */
public class WatermaskOperatorTest {

    private static WatermaskOp.Spi operatorSpi = new WatermaskOp.Spi();

    @Before
    public void setUp() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(operatorSpi);
    }

    @Test
    public void testWatermaskOperator() throws Exception {
        final HashMap<String, Object> emptyParams = new HashMap<String, Object>();
        final Product watermaskProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(WatermaskOp.class), emptyParams,
                                                           createDummyProduct());
        assertNotNull(watermaskProduct);
        assertEquals(1, watermaskProduct.getBands().length);
        final Band watermaskProductBand = watermaskProduct.getBand("Land-Water-Mask");
        assertNotNull(watermaskProductBand);
    }

    private Product createDummyProduct() throws TransformException, FactoryException {
        Product product = new Product("dummy", ProductData.TYPESTRING_UINT8, 300, 400);
        product.setGeoCoding(new CrsGeoCoding(DefaultGeographicCRS.WGS84, 300, 400, 10, 20, 0.5, 0.5));
        return product;
    }
}
