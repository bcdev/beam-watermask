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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class WatermaskClassifierTest {

    private static final String TESTIMAGE_PATH = "C:\\temp\\testimage.img";
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
        Point imagePixelCount = new Point(1024, 1024);

        int offset = watermaskClassifier.computeOffset(new Point(0, 0), new Point(1024, 1024));
        assertEquals(0, offset);

        offset = watermaskClassifier.computeOffset(new Point(10, 50), imagePixelCount);
        assertEquals(6401, offset);

        offset = watermaskClassifier.computeOffset(new Point(367, 12), imagePixelCount);
        assertEquals(1581, offset);

        offset = watermaskClassifier.computeOffset(new Point(1023, 1023), new Point(1024, 1024));
        assertEquals(131071, offset);
    }

    //    @Test
    public void testIsWater2() throws Exception {
        generateTestImage();

        assertFalse(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.0f, 0.0f));
        assertFalse(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.3f, 0.1f));
        assertFalse(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.5f, 0.1f));
        assertFalse(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.8f, 0.1f));

        assertTrue(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.0f, 0.8f));
        assertTrue(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.3f, 0.8f));
        assertTrue(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.5f, 0.8f));
        assertTrue(watermaskClassifier.isWater(new FileInputStream(TESTIMAGE_PATH), 0.8f, 0.8f));

        new File(TESTIMAGE_PATH).delete();
    }

    private void generateTestImage() throws Exception {
        FileOutputStream stream = new FileOutputStream(TESTIMAGE_PATH);
        byte[] data = new byte[1024];
        for (int i = 0; i < 512; i++) {
            data[i] = 0;
        }
        for (int i = 512; i < 1024; i++) {
            data[i] = 1;
        }
        stream.write(data);
        stream.close();
    }

}
