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

import org.junit.Before;
import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class WatermaskClassifierTest {

    private WatermaskClassifier watermaskClassifier;

    @Before
    public void setUp() throws Exception {
        watermaskClassifier = new WatermaskClassifier(1024);
    }

    @Test
    public void testIsWater() throws Exception {
        assertTrue(watermaskClassifier.isWater(5.0f, 0.0f));
        assertFalse(watermaskClassifier.isWater(5.9f, 0.0f));
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
        assertTrue(watermaskClassifier.isInRange("w000s05f.img", geoPos));
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
        geoPos.lon = -2.2;
        geoPos.lat = 45.3;
        assertTrue(watermaskClassifier.isInRange("w002n45f.img", geoPos));
    }

    @Test
    public void testComputeTileIndex() throws Exception {
        Point tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(89.99, -179.99));
        assertEquals(0, tileIndex.x);
        assertEquals(0, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(89.0, -179.0));
        assertEquals(0, tileIndex.x);
        assertEquals(0, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(88.99, -178.99));
        assertEquals(1, tileIndex.x);
        assertEquals(1, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(0.0, -0.1));
        assertEquals(179, tileIndex.x);
        assertEquals(89, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(0.0, 0.0));
        assertEquals(180, tileIndex.x);
        assertEquals(89, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(5.99, 0.99));
        assertEquals(180, tileIndex.x);
        assertEquals(84, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(4.99, -0.01));
        assertEquals(179, tileIndex.x);
        assertEquals(85, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(-89.0, 0.01));
        assertEquals(180, tileIndex.x);
        assertEquals(179, tileIndex.y);

        tileIndex = watermaskClassifier.computeTileIndex(new WatermaskClassifier.GeoPos(-0.1, 179.0));
        assertEquals(359, tileIndex.x);
        assertEquals(90, tileIndex.y);
    }
}