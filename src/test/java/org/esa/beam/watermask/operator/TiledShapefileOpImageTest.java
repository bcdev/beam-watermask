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

import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * @author Thomas Storm
 */
public class TiledShapefileOpImageTest {

    @Test
    public void testGetTileBasenames() throws Exception {
        assertEquals("w179n89", TiledShapefileOpImage.getTileBasename(0, 0));
        assertEquals("e179n89", TiledShapefileOpImage.getTileBasename(359, 0));
        assertEquals("w000n89", TiledShapefileOpImage.getTileBasename(179, 0));
        assertEquals("e000n89", TiledShapefileOpImage.getTileBasename(180, 0));

        assertEquals("e000s00", TiledShapefileOpImage.getTileBasename(180, 90));
        assertEquals("e000n00", TiledShapefileOpImage.getTileBasename(180, 89));
        assertEquals("e179s89", TiledShapefileOpImage.getTileBasename(359, 179));
        assertEquals("e179n89", TiledShapefileOpImage.getTileBasename(359, 0));

        assertEquals("w050n89", TiledShapefileOpImage.getTileBasename(129, 0));
        assertEquals("e050n89", TiledShapefileOpImage.getTileBasename(230, 0));
        assertEquals("w050n10", TiledShapefileOpImage.getTileBasename(129, 79));
        assertEquals("e050s10", TiledShapefileOpImage.getTileBasename(230, 100));
    }

    @Test
    public void testGetWrongTileBasenames() {
        int count = 0;
        try {
            TiledShapefileOpImage.getTileBasename(0, 180);
        } catch (IllegalArgumentException e) {
            count++;
        }
        try {
            TiledShapefileOpImage.getTileBasename(180, -3);
        } catch (IllegalArgumentException e) {
            count++;
        }
        try {
            TiledShapefileOpImage.getTileBasename(380, 10);
        } catch (IllegalArgumentException e) {
            count++;
        }
        try {
            TiledShapefileOpImage.getTileBasename(-3, 10);
        } catch (IllegalArgumentException e) {
            count++;
        }
        assertEquals(4, count);
    }
}
