/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Visualization from '../visualization';
import PassthroughTransformation from '../../tabledata/passthrough';

import {
  DefaultTableColumnType,
  initializeTableConfig,
  isBtnGroupWidget,
  isCheckboxWidget,
  isInputWidget,
  isOptionWidget,
  isTextareaWidget,
  parseTableOption,
  resetTableOptionConfig,
  TableColumnType,
  updateColumnTypeState,
  ValueType,
  Widget,
} from './visualization-util';

const SETTING_TEMPLATE = require('./visualization-table-setting.html');

const TABLE_OPTION_SPECS = [
  {
    name: 'useFilter',
    valueType: ValueType.BOOLEAN,
    defaultValue: false,
    widget: Widget.CHECKBOX,
    description: 'Enable filter for columns',
  },
  {
    name: 'showPagination',
    valueType: ValueType.BOOLEAN,
    defaultValue: false,
    widget: Widget.CHECKBOX,
    description: 'Enable pagination for better navigation',
  },
  {
    name: 'showAggregationFooter',
    valueType: ValueType.BOOLEAN,
    defaultValue: false,
    widget: Widget.CHECKBOX,
    description: 'Enable a footer for displaying aggregated values',
  },
];

/**
 * Visualize data in table format
 */
export default class TableVisualization extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config);
    this.passthrough = new PassthroughTransformation(config);
    this.emitTimeout = null;
    this.isRestoring = false;
    this.isUpdated = false;

    initializeTableConfig(config, TABLE_OPTION_SPECS);
  }

  getColumnMinWidth(colName) {
    let width = 150; // default
    const calculatedWidth = colName.length * 10;

    // use the broad one
    if (calculatedWidth > width) {
      width = calculatedWidth;
    }

    return width;
  }

  getSortedValue(a, b) {
    return a > b ? 1 : a === b ? 0 : -1;
  }

  createGridOptions(tableData, onRegisterApiCallback, config) {
    const rows = tableData.rows;
    const columnNames = tableData.columns.map((c) => c.name);

    const gridData = rows.map((r) => {
      return columnNames.reduce((acc, colName, index) => {
        acc[colName + index] = r[index];
        return acc;
      }, {});
    });

    const gridOptions = {
      data: gridData,
      enableGridMenu: true,
      modifierKeysToMultiSelectCells: true,
      exporterMenuCsv: true,
      exporterMenuPdf: false,
      flatEntityAccess: true,
      fastWatch: false,
      treeRowHeaderAlwaysVisible: false,
      exporterExcelFilename: 'myFile.xlsx',

      columnDefs: columnNames.map((colName, index) => {
        const self = this;
        return {
          displayName: colName,
          name: colName + index,
          type: DefaultTableColumnType,
          cellTemplate: `
            <div ng-if="!grid.getCellValue(row, col).startsWith('%html')"
                 class="ui-grid-cell-contents"><span>{{grid.getCellValue(row, col)}}</span></div>
            <div ng-if="grid.getCellValue(row, col).startsWith('%html')"
                 ng-bind-html="grid.getCellValue(row, col).split('%html')[1]"
                 class="ui-grid-cell-contents">
            </div>`,
          editableCellTemplate:
            `<div>
               <form
                 name="inputForm">
                 <textarea
                   class="ui-grid-zeppelin-special-textarea"
                   type="INPUT_TYPE"
                   ng-class="'colt' + col.uid"
                   ui-grid-editor
                   ng-model="MODEL_COL_FIELD" />
               </form>
             </div>
             `,
          minWidth: this.getColumnMinWidth(colName),
          width: '*',
          sortingAlgorithm: function(a, b, row1, row2, sortType, gridCol) {
            const colType = gridCol.colDef.type.toLowerCase();
            if (colType === TableColumnType.NUMBER) {
              return self.getSortedValue(a, b);
            } else if (colType === TableColumnType.STRING) {
              return self.getSortedValue(a.toString(), b.toString());
            } else if (colType === TableColumnType.DATE) {
              return self.getSortedValue(new Date(a), new Date(b));
            } else {
              return self.getSortedValue(a, b);
            }
          },
        };
      }),
      rowEditWaitInterval: -1, /** disable saveRow event */
      enableRowHashing: true,
      saveFocus: false,
      saveScroll: false,
      saveSort: true,
      savePinning: true,
      saveGrouping: true,
      saveGroupingExpandedStates: true,
      saveOrder: true, // column order
      saveVisible: true, // column visibility
      saveTreeView: true,
      saveFilter: true,
      saveSelection: false,
    };

    return gridOptions;
  }

  getGridElemId() {
    // angular doesn't allow `-` in scope variable name
    const gridElemId = `${this.targetEl[0].id}_grid`.replace('-', '_');
    return gridElemId;
  }

  getGridApiId() {
    // angular doesn't allow `-` in scope variable name
    const gridApiId = `${this.targetEl[0].id}_gridApi`.replace('-', '_');
    return gridApiId;
  }

  refresh() {
    const gridElemId = this.getGridElemId();
    const gridElem = angular.element(`#${gridElemId}`);

    if (gridElem) {
      gridElem.css('height', this.targetEl.height() - 10);
      const gridApiId = this.getGridApiId();
      const scope = this.getScope();
      if(scope[gridApiId]!==undefined) {
        scope[gridApiId].core.handleWindowResize();
      }
    }
  }

  refreshGrid() {
    const gridElemId = this.getGridElemId();
    const gridElem = angular.element(`#${gridElemId}`);

    if (gridElem) {
      const scope = this.getScope();
      const gridApiId = this.getGridApiId();
      scope[gridApiId].core.notifyDataChange(this._uiGridConstants.dataChange.ALL);
    }
  }

  updateColDefType(colDef, type) {
    if (type === colDef.type) {
      return;
    }

    colDef.type = type;
    const colName = colDef.name;
    const config = this.config;
    if (config.tableColumnTypeState.names && config.tableColumnTypeState.names[colName]) {
      config.tableColumnTypeState.names[colName] = type;
      this.persistConfigWithGridState(this.config);
    }
  }

  addColumnMenus(gridOptions) {
    if (!gridOptions || !gridOptions.columnDefs) {
      return;
    }

    const self = this; // for closure

    // SHOULD use `function() { ... }` syntax for each action to get `this`
    gridOptions.columnDefs.map((colDef) => {
      colDef.menuItems = [
        {
          title: 'Copy Column Name',
          action: function() {
            self.copyStringToClipboard(this.context.col.displayName);
          },
          active: function() {
            return false;
          },
        },
        {
          title: 'Type: String',
          action: function() {
            self.updateColDefType(this.context.col.colDef, TableColumnType.STRING);
          },
          active: function() {
            return this.context.col.colDef.type === TableColumnType.STRING;
          },
        },
        {
          title: 'Type: Number',
          action: function() {
            self.updateColDefType(this.context.col.colDef, TableColumnType.NUMBER);
          },
          active: function() {
            return this.context.col.colDef.type === TableColumnType.NUMBER;
          },
        },
        {
          title: 'Type: Date',
          action: function() {
            self.updateColDefType(this.context.col.colDef, TableColumnType.DATE);
          },
          active: function() {
            return this.context.col.colDef.type === TableColumnType.DATE;
          },
        },
      ];
    });
  }

  setDynamicGridOptions(gridOptions, config) {
    // parse based on their type definitions
    const parsed = parseTableOption(TABLE_OPTION_SPECS, config.tableOptionValue);

    const {showAggregationFooter, useFilter, showPagination} = parsed;

    gridOptions.showGridFooter = false;
    gridOptions.showColumnFooter = showAggregationFooter;
    gridOptions.enableFiltering = useFilter;

    gridOptions.enablePagination = showPagination;
    gridOptions.enablePaginationControls = showPagination;

    if (showPagination) {
      gridOptions.paginationPageSize = 50;
      gridOptions.paginationPageSizes = [25, 50, 100, 250, 1000];
    }

    // selection can't be rendered dynamically in ui-grid 4.0.4
    gridOptions.enableRowSelection = false;
    gridOptions.enableRowHeaderSelection = false;
    gridOptions.enableFullRowSelection = false;
    gridOptions.enableSelectAll = false;
    gridOptions.enableGroupHeaderSelection = false;
    gridOptions.enableSelectionBatchEvent = false;
  }

  append(row, columns) {
    const gridOptions = this.getGridOptions();
    this.setDynamicGridOptions(gridOptions, this.config);
    // this.refreshGrid()
    const gridElemId = this.getGridElemId();
    const gridElem = angular.element(`#${gridElemId}`);

    if (gridElem) {
      const scope = this.getScope();

      const columnNames = columns.map((c) => c.name);
      let gridData = row.map((r) => {
        return columnNames.reduce((acc, colName, index) => {
          acc[colName + index] = r[index];
          return acc;
        }, {});
      });
      gridData.map((data) => {
        scope[gridElemId].data.push(data);
      });
    }
  }

  render(tableData) {
    const gridElemId = this.getGridElemId();
    let gridElem = document.getElementById(gridElemId);

    const config = this.config;
    const self = this; // for closure
    const scope = this.getScope();
    // set gridApi for this elem
    const gridApiId = this.getGridApiId();
    const gridOptions = this.createGridOptions(tableData, onRegisterApiCallback, config);

    const onRegisterApiCallback = (gridApi) => {
      scope[gridApiId] = gridApi;
      // should restore state before registering APIs

      // register callbacks for change evens
      // should persist `self.config` instead `config` (closure issue)
      gridApi.core.on.columnVisibilityChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.colMovable.on.columnPositionChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.core.on.sortChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.core.on.filterChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.grouping.on.aggregationChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.grouping.on.groupingChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.treeBase.on.rowCollapsed(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.treeBase.on.rowExpanded(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.colResizable.on.columnSizeChanged(scope, () => {
        self.persistConfigWithGridState(self.config);
      });
      gridApi.edit.on.beginCellEdit(scope, function(rowEntity, colDef, triggerEvent) {
        let textArea = triggerEvent.currentTarget.children[1].children[0].children[0];
        textArea.style.height = textArea.scrollHeight + 'px';
        textArea.addEventListener('keydown', function() {
          let elem = this;
          setTimeout(function() {
            elem.style.height = 'auto';
            elem.style.height = elem.scrollHeight + 'px';
          });
        }, 0);
      });

      // pagination doesn't follow usual life-cycle in ui-grid v4.0.4
      // gridApi.pagination.on.paginationChanged(scope, () => { self.persistConfigWithGridState(self.config) })
      // TBD: do we need to propagate row selection?
      // gridApi.selection.on.rowSelectionChanged(scope, () => { self.persistConfigWithGridState(self.config) })
      // gridApi.selection.on.rowSelectionChangedBatch(scope, () => { self.persistConfigWithGridState(self.config) })
    };

    if (!gridElem || this.isUpdated) {
      if (this.isUpdated) {
        this.targetEl.find(gridElem).off();
        this.targetEl.find(gridElem).detach();
        this.isUpdated = false;
      }
      // create, compile and append grid elem
      gridElem = angular.element(
        `<div id="${gridElemId}" ui-grid="${gridElemId}"
              ui-grid-edit ui-grid-row-edit
              ui-grid-pagination
              ui-grid-selection
              ui-grid-cellNav ui-grid-pinning
              ui-grid-empty-base-layer
              ui-grid-resize-columns
              ui-grid-move-columns
              ui-grid-grouping
              ui-grid-save-state
              ui-grid-exporter></div>`);

      gridElem.css('height', this.targetEl.height() - 10);
      gridElem = this._compile(gridElem)(scope);
      this.targetEl.append(gridElem);
      this.setDynamicGridOptions(gridOptions, config);
      this.addColumnMenus(gridOptions);
      scope[gridElemId] = gridOptions;
      gridOptions.onRegisterApi = onRegisterApiCallback;
    } else {
      scope[gridElemId] = gridOptions;
      this.setDynamicGridOptions(gridOptions, config);
      this.refreshGrid();
    }

    const columnDefs = this.getGridOptions().columnDefs;
    updateColumnTypeState(tableData.columns, config, columnDefs);
    // SHOULD restore grid state after columnDefs are updated
    this.restoreGridState(config.tableGridState);
  }

  restoreGridState(gridState) {
    if (!gridState) {
      return;
    }

    // should set isRestoring to avoid that changed* events are triggered while restoring
    this.isRestoring = true;
    const gridApi = this.getGridApi();

    // restore grid state when gridApi is available
    if (!gridApi) {
      setTimeout(() => this.restoreGridState(gridState), 100);
    } else {
      gridApi.saveState.restore(this.getScope(), gridState);
      this.isRestoring = false;
    }
  }

  destroy() {
  }

  getTransformation() {
    return this.passthrough;
  }

  getScope() {
    const scope = this.targetEl.scope();
    return scope;
  }

  getGridOptions() {
    const scope = this.getScope();
    const gridElemId = this.getGridElemId();
    return scope[gridElemId];
  }

  getGridApi() {
    const scope = this.targetEl.scope();
    const gridApiId = this.getGridApiId();
    return scope[gridApiId];
  }

  persistConfigImmediatelyWithGridState(config) {
    this.persistConfigWithGridState(config);
  }

  persistConfigWithGridState(config) {
    if (this.isRestoring) {
      return;
    }

    const gridApi = this.getGridApi();
    config.tableGridState = gridApi.saveState.save();
    this.emitConfig(config);
  }

  persistConfig(config) {
    this.emitConfig(config);
  }

  getSetting(chart) {
    const self = this; // for closure in scope
    const configObj = self.config;

    // emit config if it's updated in `render`
    if (configObj.initialized) {
      configObj.initialized = false;
      this.persistConfig(configObj); // should persist w/o state
    } else if (configObj.tableColumnTypeState &&
      configObj.tableColumnTypeState.updated) {
      configObj.tableColumnTypeState.updated = false;
      this.persistConfig(configObj); // should persist w/o state
    }

    return {
      template: SETTING_TEMPLATE,
      scope: {
        config: configObj,
        tableOptionSpecs: TABLE_OPTION_SPECS,
        isInputWidget: isInputWidget,
        isOptionWidget: isOptionWidget,
        isCheckboxWidget: isCheckboxWidget,
        isTextareaWidget: isTextareaWidget,
        isBtnGroupWidget: isBtnGroupWidget,
        tableOptionValueChanged: () => {
          self.persistConfigWithGridState(configObj);
        },
        saveTableOption: () => {
          self.persistConfigWithGridState(configObj);
        },
        resetTableOption: () => {
          resetTableOptionConfig(configObj);
          initializeTableConfig(configObj, TABLE_OPTION_SPECS);
          self.persistConfigWithGridState(configObj);
        },
        applyTableOption: () => {
          this.isUpdated = true;
          // emit config to re-render table
          configObj.initialized = true;
          self.persistConfig(configObj);
        },
        tableWidgetOnKeyDown: (event, optSpec) => {
          const code = event.keyCode || event.which;
          if (code === 13 && isInputWidget(optSpec)) {
            self.persistConfigWithGridState(configObj);
          } else if (code === 13 && event.shiftKey && isTextareaWidget(optSpec)) {
            self.persistConfigWithGridState(configObj);
          }

          event.stopPropagation(); /** avoid to conflict with paragraph shortcuts */
        },
      },
    };
  }

  copyStringToClipboard(copyString) {
    const strToClipboard = document.createElement('textarea');
    strToClipboard.value = copyString;
    document.body.appendChild(strToClipboard);
    strToClipboard.select();
    document.execCommand('copy');
    document.body.removeChild(strToClipboard);
    return;
  }
}
