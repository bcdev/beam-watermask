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

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.gpf.operators.standard.reproject.ReprojectionOp;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Thomas Storm
 */
public class ModisProductHandler {

    private final List<Product> products = new ArrayList<Product>();
    private final List<Product> reprojectedProducts = new ArrayList<Product>();
    private final String[] args;

    ModisProductHandler(String[] args) {
        this.args = args;
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    public static void main(String[] args) throws IOException {
        final ModisProductHandler modisProductHandler = new ModisProductHandler(args);
        modisProductHandler.getProducts();
        modisProductHandler.reproject();
        modisProductHandler.write();
    }

    private void getProducts() throws IOException {
        final String inputPath = args[0];
        final File[] products = new File(inputPath).listFiles();
        GeoPos gp = new GeoPos();
        for (File file : products) {
            final Product product = ProductIO.readProduct(file);
            PixelPos sceneLL = new PixelPos(0 + 0.5f, product.getSceneRasterHeight() - 1 + 0.5f);
            final GeoCoding geoCoding = product.getGeoCoding();
            geoCoding.getGeoPos(sceneLL, gp);
            if (gp.getLat() <= -60.0f) {
                this.products.add(product);
                System.out.println(MessageFormat.format(
                        "Added product ''{0}'' to products because lower left lat is ''{1}''.",
                        product.toString(),
                        gp.getLat()));
                continue;
            }
            PixelPos sceneLR = new PixelPos(product.getSceneRasterWidth() - 1 + 0.5f,
                                            product.getSceneRasterHeight() - 1 + 0.5f);
            geoCoding.getGeoPos(sceneLR, gp);
            if (gp.getLat() <= -60.0f) {
                System.out.println(MessageFormat.format(
                        "Added product ''{0}'' to products because lower right lat is ''{1}''.",
                        product.toString(),
                        gp.getLat()));
                this.products.add(product);
                continue;
            }
            PixelPos sceneUL = new PixelPos(0.5f, 0.5f);
            geoCoding.getGeoPos(sceneUL, gp);
            if (gp.getLat() >= 60.0f) {
                System.out.println(MessageFormat.format(
                        "Added product ''{0}'' to products because upper left lat is ''{1}''.",
                        product.toString(),
                        gp.getLat()));
                this.products.add(product);
                continue;
            }
            PixelPos sceneUR = new PixelPos(product.getSceneRasterWidth() - 1 + 0.5f, 0.5f);
            geoCoding.getGeoPos(sceneUR, gp);
            if (gp.getLat() >= 60.0f) {
                System.out.println(MessageFormat.format(
                        "Added product ''{0}'' to products because upper right lat is ''{1}''.",
                        product.toString(),
                        gp.getLat()));
                this.products.add(product);
            }
        }
    }

    private void reproject() {
        final Map<String, Object> params = new HashMap<String, Object>();
        params.put("crs", "EPSG:4326");
        for (Product belowSixtyProduct : products) {
            System.out.println("Reprojecting product '" + belowSixtyProduct + "'.");
            final Product reprojectedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ReprojectionOp.class),
                                                                 params, belowSixtyProduct);
            reprojectedProducts.add(reprojectedProduct);
        }
    }

    private void write() throws IOException {
        for (Product reprojectedProduct : reprojectedProducts) {
            System.out.println("Writing product '" + reprojectedProduct + "'.");
            reprojectedProduct.removeBand(reprojectedProduct.getBand("water_mask_QA"));
            ProductIO.writeProduct(reprojectedProduct, new File(args[1], getProductName(reprojectedProduct)), "BEAM-DIMAP", false);
        }
    }

    private String getProductName(Product reprojectedProduct) {
        return reprojectedProduct.getName();
    }

}
