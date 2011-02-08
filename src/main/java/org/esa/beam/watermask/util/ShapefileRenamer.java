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

import java.io.File;

public class ShapefileRenamer {

    public static void main(String[] args) {
        renameFiles(args[0]);
    }

    static void renameFiles(String dir) {
        File path = new File(dir);
        final File[] files = path.listFiles();
        for (File file : files) {
            final String oldName = file.getName();
            String firstPart = oldName.substring(0, 7);
            String secondPart = oldName.substring(8, 12);
            String newName = firstPart + secondPart;
            if (!file.renameTo(new File(file.getParent(), newName))) {
                throw new IllegalStateException(
                        "something didn't work renaming '" + file.getName() + "' to '" + newName + "'.");
            }
        }
    }

}
