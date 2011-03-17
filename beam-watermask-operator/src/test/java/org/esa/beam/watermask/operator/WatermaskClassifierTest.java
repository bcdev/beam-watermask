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
import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class WatermaskClassifierTest {

    private WatermaskClassifier classifier;
    private WatermaskClassifier fillClassifier;

    @Before
    public void setUp() throws Exception {
        classifier = new WatermaskClassifier(WatermaskClassifier.RESOLUTION_50, false);
        fillClassifier = new WatermaskClassifier(WatermaskClassifier.RESOLUTION_50, true);
    }

    @Test
    public void testFill() throws Exception {
        assertTrue(fillClassifier.isWater(42.908833f, 5.5034647f));
        assertTrue(fillClassifier.isWater(42.092968f, 4.950571f));
    }

    @Test
    public void testIsWater() throws Exception {

        assertFalse(classifier.isWater(30.30539f, 111.55285f));
        assertTrue(classifier.isWater(30.269484f, 111.55418f));

        assertFalse(classifier.isWater(49.68f, 0.581f));
        assertTrue(classifier.isWater(49.434505f, 0.156014f));

        assertTrue(classifier.isWater(49.33615f, -0.0096f));
        assertFalse(classifier.isWater(49.32062f, -0.005918f));

        assertFalse(classifier.isWater(46.5f, 0.5f));

        assertTrue(classifier.isWater(5.01f, 0.01f));
        assertTrue(classifier.isWater(5.95f, 0.93f));
        assertTrue(classifier.isWater(5.04f, 0.95f));

        assertTrue(classifier.isWater(5.5f, 0.5f));
        assertFalse(classifier.isWater(5.88f, 0.24f));

        assertTrue(classifier.isWater(43.322360f, 4.157f));
        assertTrue(classifier.isWater(43.511243f, 3.869841f));

        assertFalse(classifier.isWater(45.981416f, -84.462957f));
        assertTrue(classifier.isWater(45.967423f, -84.477179f));

        assertTrue(classifier.isWater(53.5f, 5.92f));
        assertFalse(classifier.isWater(53.458760f, 5.801733f));

        assertTrue(classifier.isWater(-4.347463f, 11.443256f));
        assertFalse(classifier.isWater(-4.2652f, 11.49324f));
    }

    @Test
    public void testGetZipfile() throws Exception {
        // north-west

        assertEquals("w002n51.img", classifier.createImgFileName(51.007f, -1.30f));
        assertFalse("w001n51.img".equals(classifier.createImgFileName(51.007f, -1.30f)));

        assertEquals("w002n48.img", classifier.createImgFileName(48.007f, -1.83f));
        assertFalse("w001n48.img".equals(classifier.createImgFileName(48.007f, -1.83f)));

        // north-east

        assertEquals("e000n51.img", classifier.createImgFileName(51.007f, 0.30f));
        assertFalse("e001n51.img".equals(classifier.createImgFileName(51.007f, 0.30f)));

        assertEquals("e000n49.img", classifier.createImgFileName(49.993961334228516f, 0.006230226717889309f));
        assertFalse("w001n49.img".equals(classifier.createImgFileName(51.007f, 0.30f)));

        assertEquals("e001n51.img", classifier.createImgFileName(51.007f, 1.30f));
        assertFalse("e000n51.img".equals(classifier.createImgFileName(51.007f, 1.30f)));

        assertEquals("e000n45.img", classifier.createImgFileName(45.001f, 0.005f));
        assertFalse("w000n45.img".equals(classifier.createImgFileName(45.001f, 0.005f)));

        assertEquals("e111n30.img", classifier.createImgFileName(30.27f, 111.581f));
        assertFalse("e111n30.img".equals(classifier.createImgFileName(29.01f, 112.01f)));

        // south-west

        assertEquals("w001s01.img", classifier.createImgFileName(-0.01f, -0.30f));
        assertFalse("w000s01.img".equals(classifier.createImgFileName(-0.01f, -0.30f)));

        assertEquals("w002s02.img", classifier.createImgFileName(-1.01f, -1.30f));
        assertFalse("w001s01.img".equals(classifier.createImgFileName(-1.01f, -1.30f)));

        // south-east

        assertEquals("e000s01.img", classifier.createImgFileName(-0.01f, 0.30f));
        assertFalse("e000s00.img".equals(classifier.createImgFileName(-0.01f, 0.30f)));

        assertEquals("e001s01.img", classifier.createImgFileName(-0.01f, 1.30f));
        assertFalse("e001s00.img".equals(classifier.createImgFileName(-0.01f, 1.30f)));
    }

    @Test
    public void testGeoPosToPixel() throws Exception {
        Point pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0f, 0.0f);
        assertEquals(0, pixelPos.x);
        assertEquals(1023, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0f, 0.5f);
        assertEquals(512, pixelPos.x);
        assertEquals(1023, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0f, 0.0009765f);
        assertEquals(0, pixelPos.x);
        assertEquals(1023, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0f, 0.0009766f);
        assertEquals(1, pixelPos.x);
        assertEquals(1023, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0f, 0.99999f);
        assertEquals(1023, pixelPos.x);
        assertEquals(1023, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.5f, 0.0f);
        assertEquals(0, pixelPos.x);
        assertEquals(511, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0009765f, 0.5f);
        assertEquals(512, pixelPos.x);
        assertEquals(1023, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.0009766f, 0.5f);
        assertEquals(512, pixelPos.x);
        assertEquals(1022, pixelPos.y);

        pixelPos = WatermaskClassifier.geoPosToPixel(1024, 1024, 0.99999f, 0.0f);
        assertEquals(0, pixelPos.x);
        assertEquals(0, pixelPos.y);
    }

    @Test
    public void testGetResource() throws Exception {
        URL resource = getClass().getResource("image.properties");
        assertNotNull(resource);
    }
}