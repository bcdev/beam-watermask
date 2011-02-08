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

import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;


/**
 * @author Thomas Storm
 */
public class ShapefileRenamerTest {

    @Test
    public void testRenaming() throws Exception {
        final URL someResource = getClass().getResource("e000n05f.img");
        final String someResourceFile = someResource.getFile();
        ShapefileRenamer.renameFiles(new File(someResourceFile).getParent());

        final URL renamedFile1 = getClass().getResource("e000n05.img");
        assertNotNull(renamedFile1);

        final URL renamedFile2 = getClass().getResource("e000n06.img");
        assertNotNull(renamedFile2);

        final URL renamedFile3 = getClass().getResource("w010n06.img");
        assertNotNull(renamedFile3);

        new File(renamedFile1.getFile()).delete();
        new File(renamedFile2.getFile()).delete();
        new File(renamedFile3.getFile()).delete();
    }
}
