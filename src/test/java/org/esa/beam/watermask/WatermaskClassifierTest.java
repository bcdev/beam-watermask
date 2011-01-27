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
        assertTrue(watermaskClassifier.isWater(0.5f, 5.5f));
        assertFalse(watermaskClassifier.isWater(0.25f, 5.9f));
    }

    @Test
    public void testGeoPosToPixel() throws Exception {
        checkPixelPosLat(0.8, 80, 100);
        checkPixelPosLon(0.8, 80, 100);
        checkPixelPosLat(123.45, 45, 100);
        checkPixelPosLon(88.12, 12, 100);
        checkPixelPosLat(123.45, 450, 1000);
        checkPixelPosLon(88.12, 120, 1000);
        checkPixelPosLat(10, 0, 367);
        checkPixelPosLon(20, 0, 512);
        checkPixelPosLat(20.7, 358, 512);
        checkPixelPosLon(10.5, 183, 367);
    }

    private void checkPixelPosLat(double lat, int expectedY, final int height) {
        WatermaskClassifier.Pixel pixelPos;
        WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(lat, 0);
        pixelPos = watermaskClassifier.geoPosToPixel(0, height, geoPos);
        assertEquals(expectedY, pixelPos.y);
    }

    private void checkPixelPosLon(double lon, int expectedX, final int width) {
        WatermaskClassifier.Pixel pixelPos;
        WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(0, lon);
        pixelPos = watermaskClassifier.geoPosToPixel(width, 0, geoPos);
        assertEquals(expectedX, pixelPos.x);
    }

    @Test
    public void testIsInRange() throws Exception {
        final WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(0.6, 5.9);
        assertTrue(watermaskClassifier.isInRange("e000n05f.img", geoPos));
        geoPos.lat = 0.9;
        geoPos.lon = 5.0;
        assertTrue(watermaskClassifier.isInRange("e000n05f.img", geoPos));
        geoPos.lat = -0.1;
        geoPos.lon = 5.0;
        assertTrue(watermaskClassifier.isInRange("w000n05f.img", geoPos));
        geoPos.lat = 0.1;
        geoPos.lon = -5.0;
        assertTrue(watermaskClassifier.isInRange("e000s05f.img", geoPos));
        geoPos.lat = -0.1;
        geoPos.lon = -5.0;
        assertTrue(watermaskClassifier.isInRange("w000s05f.img", geoPos));
        geoPos.lat = 0.1;
        geoPos.lon = 4.99;
        assertFalse(watermaskClassifier.isInRange("e000n05f.img", geoPos));
        geoPos.lat = -120.53;
        geoPos.lon = -53.71;
        assertTrue(watermaskClassifier.isInRange("w120s53f.img", geoPos));
        geoPos.lat = 179;
        geoPos.lon = 10;
        assertFalse(watermaskClassifier.isInRange("e000n09f.img", geoPos));
    }

    @Test
    public void testFindingImage() throws IOException {
        final WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(0.6, 9.8);
        assertNotNull(watermaskClassifier.findImage(geoPos));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindingNoImage() throws Exception {
        final WatermaskClassifier.GeoPos geoPos = new WatermaskClassifier.GeoPos(179, 10);
        watermaskClassifier.findImage(geoPos);
    }
}
