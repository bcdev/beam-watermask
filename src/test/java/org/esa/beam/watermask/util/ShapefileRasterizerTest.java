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

package org.esa.beam.watermask.util;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * TODO fill out or delete
 *
 * @author Thomas Storm
 */
public class ShapefileRasterizerTest {

    private static final int SIDE_LENGTH = ShapeFileRasterizer.computeSideLength(50);

    @Test
    public void testRasterize() throws Exception {
        ShapeFileRasterizer.tileSize = new Point(SIDE_LENGTH, SIDE_LENGTH);
        final ShapeFileRasterizer rasterizer = new ShapeFileRasterizer(new File("C:\\temp\\"));
        final BufferedImage bufferedImage = rasterizer.createImage(new File("C:\\temp\\e000n05f.shp"));
        ImageIO.write(bufferedImage, "png", new File("C:\\temp\\output.png"));
    }
}
