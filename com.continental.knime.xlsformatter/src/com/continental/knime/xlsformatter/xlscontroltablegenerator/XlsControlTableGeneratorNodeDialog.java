/*
 * Continental Nodes for KNIME
 * Copyright (C) 2019  Continental AG, Hanover, Germany
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.continental.knime.xlsformatter.xlscontroltablegenerator;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentButtonGroup;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

import com.continental.knime.xlsformatter.commons.XlsFormatterControlTableAnalysisTools;
import com.continental.knime.xlsformatter.commons.XlsFormatterUiOptions;
import com.continental.knime.xlsformatter.xlscontroltablegenerator.XlsControlTableGeneratorNodeModel.InconsistencyResolutionOptions;
import com.continental.knime.xlsformatter.xlscontroltablegenerator.XlsControlTableGeneratorNodeModel.OperationType;

public class XlsControlTableGeneratorNodeDialog extends DefaultNodeSettingsPane {

	// the logger instance
	private static final NodeLogger logger = NodeLogger
			.getLogger(XlsControlTableGeneratorNodeDialog.class);
	
	SettingsModelBoolean shiftRows;
	SettingsModelBoolean unpivot;
	SettingsModelBoolean extendedUnpivotColumns;
	SettingsModelString inconsistencyResolutionStrategy;
	SettingsModelString operationType;
	
	private static final String[] INCONSISTENCY_RESOLUTION_STRATEGY_ARRAY = XlsFormatterUiOptions.getDropdownArrayFromEnum(InconsistencyResolutionOptions.values(), false);
	private static final String[] OPERATION_TYPE_ARRAY = XlsFormatterUiOptions.getDropdownArrayFromEnum(OperationType.values(), false);
	
	ChangeListener changeListener = new ControlTableGeneratorDialogChangeListener();

	/**
	 * Indicates whether the input table is an arbitrary table (-1) or if it is one of the unpivoted
	 * options generated by a different instance of this node itself (length 3 for standard or 8 for extended)
	 */
	protected int longUnpivotedInputTableColumnCount = -1; // will be set with last spec info in overwritten loadAdditionalSettingsFrom method
	
	
	protected XlsControlTableGeneratorNodeDialog() {
		super();

		
		this.createNewGroup("Operation Type (automatically set based on the provided input table)");
		setHorizontalPlacement(true);
		operationType = new SettingsModelString(XlsControlTableGeneratorNodeModel.CFGKEY_OPERATION_TYPE,
				longUnpivotedInputTableColumnCount == -1 ?
						OperationType.STANDARD.toString() : OperationType.PIVOT_BACK.toString());
		DialogComponentButtonGroup operationTypeComponent = new DialogComponentButtonGroup(
				operationType, "", true, OPERATION_TYPE_ARRAY, OPERATION_TYPE_ARRAY);
		operationTypeComponent.setToolTipText("The operation type is set automatically depending on the connected control table.");
		this.addDialogComponent(operationTypeComponent);
		setHorizontalPlacement(false);
		
		this.createNewGroup("Shift Rows Option");
		shiftRows = new SettingsModelBoolean(XlsControlTableGeneratorNodeModel.CFGKEY_ROW_SHIFT, XlsControlTableGeneratorNodeModel.DEFAULT_ROW_SHIFT);
		this.addDialogComponent(new DialogComponentBoolean(
				shiftRows, "write column header to first row"));

		this.createNewGroup("Result Table Structure Options");
		unpivot = new SettingsModelBoolean(XlsControlTableGeneratorNodeModel.CFGKEY_UNPIVOT, XlsControlTableGeneratorNodeModel.DEFAULT_UNPIVOT);
		this.addDialogComponent(new DialogComponentBoolean(
				unpivot, "unpivot result table (for easier post-processing and re-pivoting)"));

		extendedUnpivotColumns = new SettingsModelBoolean(XlsControlTableGeneratorNodeModel.CFGKEY_EXTENDED_UNPIVOT_COLUMNS, XlsControlTableGeneratorNodeModel.DEFAULT_EXTENDED_UNPIVOT_COLUMNS);
		this.addDialogComponent(new DialogComponentBoolean(
				extendedUnpivotColumns, "add additional header columns"));
		
		unpivot.addChangeListener(changeListener);
		
		this.createNewGroup("Contradiction Resolution Strategy at Operation Type 'long to wide'");
		inconsistencyResolutionStrategy = new SettingsModelString(XlsControlTableGeneratorNodeModel.CFGKEY_INCONSISTENCY_RESOLUTION_STRATEGY, XlsControlTableGeneratorNodeModel.DEFAULT_INCONSISTENCY_RESOLUTION_STRATEGY);
		DialogComponentStringSelection inconsistencyResolutionStrategyComponent = new DialogComponentStringSelection(
				inconsistencyResolutionStrategy, "how to deal with contradicting information?", INCONSISTENCY_RESOLUTION_STRATEGY_ARRAY);
		inconsistencyResolutionStrategyComponent.setToolTipText("In case the extended columns hold contradicting addressing information (e.g. in A2 in 'Cell' and 3 in 'Column number'), which information shall be taken to locate the cell on the sheet?");
		this.addDialogComponent(inconsistencyResolutionStrategyComponent);
		
	}
	
	class ControlTableGeneratorDialogChangeListener implements ChangeListener {
		public void stateChanged(final ChangeEvent e) { 
			shiftRows.setEnabled(longUnpivotedInputTableColumnCount == -1);
			unpivot.setEnabled(longUnpivotedInputTableColumnCount == -1);
			extendedUnpivotColumns.setEnabled(longUnpivotedInputTableColumnCount == -1 && unpivot.getBooleanValue());
			inconsistencyResolutionStrategy.setEnabled(longUnpivotedInputTableColumnCount == 8); // at option 3, no inconsistencies can occur
		}
	}
	
	@Override
	public void loadAdditionalSettingsFrom(NodeSettingsRO settings, DataTableSpec[] specs)
			throws NotConfigurableException {
		super.loadAdditionalSettingsFrom(settings, specs);
		
		DataTableSpec spec = (DataTableSpec)specs[0];
  	if (spec.getNumColumns() == 0)
  		throw new NotConfigurableException("\nThis node cannot be configured without a non-empty input table specification.");
  	
  	longUnpivotedInputTableColumnCount = XlsFormatterControlTableAnalysisTools.isLongControlTableSpec(spec, null, logger);
		operationType.setStringValue(longUnpivotedInputTableColumnCount == -1 ?
				OperationType.STANDARD.toString() : OperationType.PIVOT_BACK.toString());
		operationType.setEnabled(false);
		
		if (longUnpivotedInputTableColumnCount != 8)
			inconsistencyResolutionStrategy.setStringValue(InconsistencyResolutionOptions.FAIL.toString());
  	
  	changeListener.stateChanged(null);
	}
}

