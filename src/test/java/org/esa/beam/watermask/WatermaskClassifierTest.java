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

package org.esa.beam.watermask;

import org.junit.Before;
import org.junit.Test;

import java.awt.Point;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class WatermaskClassifierTest {

    private WatermaskClassifier watermaskClassifier;

    @Before
    public void setUp() throws Exception {
        watermaskClassifier = new WatermaskClassifier();
    }

    @Test
    public void testIsWater() throws Exception {
        assertTrue(watermaskClassifier.isWater(5.0f, 0.0f));
        assertFalse(watermaskClassifier.isWater(5.9f, 0.0f));
        assertTrue(watermaskClassifier.isWater(5.9f, 0.9f));
        assertTrue(watermaskClassifier.isWater(5.0f, 0.9f));

        assertTrue(watermaskClassifier.isWater(5.5f, 0.5f));
        assertFalse(watermaskClassifier.isWater(5.88f, 0.24f));
    }

    @Test
    public void testIsInRange() throws Exception {
        final WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(5.9, 0.6);
        assertTrue(watermaskClassifier.isInRange("e000n05f.img", geoPos));
        geoPos.lon = 0.9;
        geoPos.lat = 5.0;
        assertTrue(watermaskClassifier.isInRange("e000n05f.img", geoPos));
        geoPos.lon = -0.1;
        geoPos.lat = -5.0;
        assertTrue(watermaskClassifier.isInRange("w000n05f.img", geoPos));
        geoPos.lon = 0.1;
        geoPos.lat = -5.001;
        assertTrue(watermaskClassifier.isInRange("e000s05f.img", geoPos));
        geoPos.lon = -0.1;
        geoPos.lat = -5.0;
        assertTrue(watermaskClassifier.isInRange("w000s05f.img", geoPos));
        geoPos.lon = 0.1;
        geoPos.lat = 4.99;
        assertFalse(watermaskClassifier.isInRange("e000n05f.img", geoPos));
        geoPos.lon = -120.53;
        geoPos.lat = -53.71;
        assertTrue(watermaskClassifier.isInRange("w120s53f.img", geoPos));
        geoPos.lon = 179;
        geoPos.lat = 10;
        assertFalse(watermaskClassifier.isInRange("e000n09f.img", geoPos));
    }

    @Test
    public void testFindingImage() throws IOException {
        final WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(9.8, 0.6);
        assertNotNull(watermaskClassifier.findImage(geoPos));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindingNoImage() throws Exception {
        final WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(179, 10);
        watermaskClassifier.findImage(geoPos);
    }

    @Test
    public void testComputeOffset() throws Exception {
        int offset = watermaskClassifier.computeStreamOffset(new Point(0, 0), new Point(5, 5));
        assertEquals(0, offset);

        offset = watermaskClassifier.computeStreamOffset(new Point(1, 0), new Point(5, 5));
        assertEquals(1/8, offset);

        offset = watermaskClassifier.computeStreamOffset(new Point(4, 0), new Point(5, 5));
        assertEquals(4/8, offset);

        offset = watermaskClassifier.computeStreamOffset(new Point(0, 1), new Point(5, 5));
        assertEquals(5/8, offset);

        offset = watermaskClassifier.computeStreamOffset(new Point(0, 2), new Point(5, 5));
        assertEquals(10/8, offset);

        offset = watermaskClassifier.computeStreamOffset(new Point(4, 4), new Point(5, 5));
        assertEquals(24/8, offset);
    }

    @Test
    public void testToBinaryString() throws Exception {
        byte b = 0;
        String s = WatermaskClassifier.toBinaryString(b);
        assertEquals("00000000", s);

        b = 64;
        s = WatermaskClassifier.toBinaryString(b);
        assertEquals("01000000", s);

        b = 127;
        s = WatermaskClassifier.toBinaryString(b);
        assertEquals("01111111", s);

        b = -128;
        s = WatermaskClassifier.toBinaryString(b);
        assertEquals("10000000", s);
    }
}