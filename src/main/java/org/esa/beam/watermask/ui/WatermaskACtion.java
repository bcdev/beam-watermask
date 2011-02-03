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

package org.esa.beam.watermask.ui;

import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.watermask.operator.WatermaskOp;

/**
 * Action class for Watermask operator.
 *
 * @author Thomas Storm
 */
public class WatermaskAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent commandEvent) {
        new DefaultSingleTargetProductDialog(OperatorSpi.getOperatorAlias(WatermaskOp.class), getAppContext(),
                                             "Accurate Land/Water-mask", "watermask").show();
    }
}
