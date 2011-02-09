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

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class TiledShapefileOpImageTest {

    @Test
    public void testGetTileName() throws Exception {
        assertEquals("w180n89.img", TiledShapefileOpImage.getTilename(0, 0));
        assertEquals("e179n89.img", TiledShapefileOpImage.getTilename(359, 0));
        assertEquals("w180s90.img", TiledShapefileOpImage.getTilename(0, 179));
        assertEquals("e179s90.img", TiledShapefileOpImage.getTilename(359, 179));
        assertEquals("w001n49.img", TiledShapefileOpImage.getTilename(179, 40));
        assertEquals("w085n45.img", TiledShapefileOpImage.getTilename(95, 44));
        assertEquals("e011s05.img", TiledShapefileOpImage.getTilename(191, 94));
    }
}
