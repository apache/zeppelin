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

import Visualization from '../visualization'
import PassthroughTransformation from '../../tabledata/passthrough'

import {
  Widget, ValueType,
  isInputWidget, isOptionWidget, isCheckboxWidget,
  isTextareaWidget, isBtnGroupWidget,
  initializeTableConfig, resetTableOptionConfig,
  parseTableOption,
} from './visualization-util'

const SETTING_TEMPLATE = require('./visualization-table-setting.html')

const TableColumnType = {
  STRING: 'string',
  BOOLEAN: 'boolean',
  NUMBER: 'number',
  DATE: 'date',
  OBJECT: 'object',
  NUMBER_STR: 'numberStr',
}

const TABLE_OPTION_SPECS = [
  {
    name: 'showGridFooter',
    valueType: ValueType.BOOLEAN,
    defaultValue: false,
    widget: Widget.CHECKBOX,
    // eslint-disable-next-line max-len
    description: '<a href="http://ui-grid.info/docs/#/api/ui.grid.class:GridOptions#properties_showgridfooter">gridOptions.showGridFooter</a>',
  },
  {
    name: 'showColumnFooter',
    valueType: ValueType.BOOLEAN,
    defaultValue: true,
    widget: Widget.CHECKBOX,
    // eslint-disable-next-line max-len
    description: '<a href="http://ui-grid.info/docs/#/api/ui.grid.class:GridOptions#properties_showcolumnfooter">gridOptions.showColumnFooter</a>',
  },
  {
    name: 'showPagination',
    valueType: ValueType.BOOLEAN,
    defaultValue: true,
    widget: Widget.CHECKBOX,
    // eslint-disable-next-line max-len
    description: '<a href="http://ui-grid.info/docs/#/api/ui.grid.pagination.api:GridOptions#properties_enablepaginationcontrols">gridOptions.enablePaginationControls</a>',
  },
  {
    name: 'useFilter',
    valueType: ValueType.BOOLEAN,
    defaultValue: false,
    widget: Widget.CHECKBOX,
    // eslint-disable-next-line max-len
    description: '<a href="http://ui-grid.info/docs/#/api/ui.grid.class:GridOptions#properties_enablefiltering">gridOptions.enableFiltering</a>',
  },
  {
    name: 'defaultPaginationSize',
    valueType: ValueType.INT,
    defaultValue: 50,
    widget: Widget.INPUT,
    // eslint-disable-next-line max-len
    description: '<a href="http://ui-grid.info/docs/#/api/ui.grid.pagination.api:GridOptions#properties_paginationpagesize">gridOptions.paginationPageSize</a>',
  },
  {
    name: 'availablePaginationSizes',
    valueType: ValueType.JSON,
    defaultValue: '[25, 50, 100, 250, 1000]', // JSON's defaultValue should be string type
    widget: Widget.TEXTAREA,
    // eslint-disable-next-line max-len
    description: '<a href="http://ui-grid.info/docs/#/api/ui.grid.pagination.api:GridOptions#properties_paginationpagesizes">gridOptions.paginationPageSizes</a>',
  },
]

/**
 * Visualize data in table format
 */
export default class TableVisualization extends Visualization {
  constructor (targetEl, config) {
    super(targetEl, config)
    this.passthrough = new PassthroughTransformation(config)
    this.emitTimeout = null
    this.isRestoring = false

    initializeTableConfig(config, TABLE_OPTION_SPECS)
  }

  createGridOptions(tableData, onRegisterApiCallback, config) {
    const rows = tableData.rows
    const columnNames = tableData.columns.map(c => c.name)

    const gridData = rows.map(r => {
      return columnNames.reduce((acc, colName, index) => {
        acc[colName] = r[index]
        return acc
      }, {})
    })

    const gridOptions = {
      data: gridData,
      enableGridMenu: true,
      modifierKeysToMultiSelectCells: true,
      exporterMenuCsv: true,
      exporterMenuPdf: false,
      flatEntityAccess: true,
      fastWatch: false,
      enableGroupHeaderSelection: true,
      enableSelectionBatchEvent: true,
      treeRowHeaderAlwaysVisible: false,
      columnDefs: columnNames.map(colName => {
        return {
          name: colName,
          type: TableColumnType.STRING,
        }
      }),
      rowEditWaitInterval: -1, /** disable saveRow event */
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
    }

    return gridOptions
  }

  getGridElemId() {
    // angular doesn't allow `-` in scope variable name
    const gridElemId = `${this.targetEl[0].id}_grid`.replace('-', '_')
    return gridElemId
  }

  getGridApiId() {
    // angular doesn't allow `-` in scope variable name
    const gridApiId = `${this.targetEl[0].id}_gridApi`.replace('-', '_')
    return gridApiId
  }

  refresh() {
    const gridElemId = this.getGridElemId()
    const gridElem = angular.element(`#${gridElemId}`)

    if (gridElem) {
      gridElem.css('height', this.targetEl.height() - 10)
    }
  }

  refreshGrid() {
    const gridElemId = this.getGridElemId()
    const gridElem = angular.element(`#${gridElemId}`)

    if (gridElem) {
      const scope = this.getScope()
      const gridApiId = this.getGridApiId()
      scope[gridApiId].core.notifyDataChange(this._uiGridConstants.dataChange.ALL)
    }
  }

  refreshGridColumn() {
    const gridElemId = this.getGridElemId()
    const gridElem = angular.element(`#${gridElemId}`)

    if (gridElem) {
      const scope = this.getScope()
      const gridApiId = this.getGridApiId()
      scope[gridApiId].core.notifyDataChange(this._uiGridConstants.dataChange.COLUMN)
    }
  }

  addColumnMenus(gridOptions) {
    if (!gridOptions || !gridOptions.columnDefs) { return }

    // SHOULD use `function() { ... }` syntax for each action to get `this`
    gridOptions.columnDefs.map(colDef => {
      colDef.menuItems = [
        {
          title: 'Type: String',
          action: function() {
            this.context.col.colDef.type = TableColumnType.STRING
          },
          active: function() {
            return this.context.col.colDef.type === TableColumnType.STRING
          },
        },
        {
          title: 'Type: Number',
          action: function() {
            this.context.col.colDef.type = TableColumnType.NUMBER
          },
          active: function() {
            return this.context.col.colDef.type === TableColumnType.NUMBER
          },
        },
        {
          title: 'Type: Date',
          action: function() {
            this.context.col.colDef.type = TableColumnType.DATE
          },
          active: function() {
            return this.context.col.colDef.type === TableColumnType.DATE
          },
        },
      ]
    })
  }

  setDynamicGridOptions(gridOptions, config) {
    // parse based on their type definitions
    const parsed = parseTableOption(TABLE_OPTION_SPECS, config.tableOptionValue)

    const {
      showGridFooter, showColumnFooter,
      useFilter, showPagination,
      defaultPaginationSize, availablePaginationSizes,
    } = parsed

    gridOptions.showGridFooter = showGridFooter
    gridOptions.showColumnFooter = showColumnFooter
    gridOptions.enableFiltering = useFilter

    gridOptions.enablePagination = showPagination
    gridOptions.enablePaginationControls = showPagination
    gridOptions.paginationPageSize = defaultPaginationSize
    gridOptions.paginationPageSizes = availablePaginationSizes
  }

  render (tableData) {
    const gridElemId = this.getGridElemId()
    let gridElem = document.getElementById(gridElemId)

    const config = this.config
    const self = this // for closure

    if (!gridElem) {
      // create, compile and append grid elem
      gridElem = angular.element(
        `<div id="${gridElemId}" ui-grid="${gridElemId}"
              ui-grid-edit ui-grid-row-edit 
              ui-grid-pagination ui-grid-selection
              ui-grid-cellNav ui-grid-pinning
              ui-grid-empty-base-layer
              ui-grid-resize-columns ui-grid-move-columns
              ui-grid-grouping
              ui-grid-save-state
              ui-grid-exporter></div>`)

      gridElem.css('height', this.targetEl.height() - 10)
      const scope = this.getScope()
      gridElem = this._compile(gridElem)(scope)
      this.targetEl.append(gridElem)

      // set gridOptions for this elem
      const gridOptions = this.createGridOptions(tableData, onRegisterApiCallback, config)
      this.setDynamicGridOptions(gridOptions, config)
      this.addColumnMenus(gridOptions)
      scope[gridElemId] = gridOptions

      // set gridApi for this elem
      const gridApiId = this.getGridApiId()
      const onRegisterApiCallback = (gridApi) => {
        scope[gridApiId] = gridApi
        // should restore state before registering APIs

        // register callbacks for change evens
        // should persist `self.config` instead `config` (closure issue)
        gridApi.core.on.columnVisibilityChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.colMovable.on.columnPositionChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.core.on.sortChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.core.on.filterChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.grouping.on.aggregationChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.grouping.on.groupingChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.treeBase.on.rowCollapsed(scope, () => { self.persistConfigWithGridState(self.config) })
        gridApi.treeBase.on.rowExpanded(scope, () => { self.persistConfigWithGridState(self.config) })

        // pagination doesn't follow usual life-cycle in ui-grid v4.0.4
        // gridApi.pagination.on.paginationChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        // TBD: do we need to propagate row selection?
        // gridApi.selection.on.rowSelectionChanged(scope, () => { self.persistConfigWithGridState(self.config) })
        // gridApi.selection.on.rowSelectionChangedBatch(scope, () => { self.persistConfigWithGridState(self.config) })
      }
      gridOptions.onRegisterApi = onRegisterApiCallback
    } else {
      // don't need to update gridOptions.data since it's synchronized by paragraph execution
      const gridOptions = this.getGridOptions()
      this.setDynamicGridOptions(gridOptions, config)
      this.refreshGrid()
    }

    this.restoreGridState(config.tableGridState)
  }

  restoreGridState(gridState) {
    if (!gridState) { return }

    // should set isRestoring to avoid that changed* events are triggered while restoring
    this.isRestoring = true
    const gridApi = this.getGridApi()

    // restore grid state when gridApi is available
    if (!gridApi) {
      setTimeout(() => this.restoreGridState(gridState), 100)
    } else {
      gridApi.saveState.restore(this.getScope(), gridState)
      this.isRestoring = false
    }
  }

  destroy () {
  }

  getTransformation () {
    return this.passthrough
  }

  getScope() {
    const scope = this.targetEl.scope()
    return scope
  }

  getGridOptions() {
    const scope = this.getScope()
    const gridElemId = this.getGridElemId()
    return scope[gridElemId]
  }

  getGridApi() {
    const scope = this.targetEl.scope()
    const gridApiId = this.getGridApiId()
    return scope[gridApiId]
  }

  persistConfigImmediatelyWithGridState(config) {
    this.persistConfigWithGridState(config, 0)
  }

  persistConfigWithGridState(config, millis) {
    if (this.isRestoring) { return }
    // use timeout to avoid recursive emitting
    if (this.emitTimeout) {
       // if there is already a timeout in process cancel it
      this._timeout.cancel(this.emitTimeout)
    }

    if (typeof millis === 'undefined') { millis = 1000 }

    const self = this // closure

    this.emitTimeout = this._timeout(() => {
      const gridApi = self.getGridApi()
      config.tableGridState = gridApi.saveState.save()
      self.emitConfig(config)

      const gridOptions = self.getGridOptions()
      console.warn(gridOptions.columnDefs)

      self.emitTimeout = null // reset timeout
    }, millis)
  }

  persistConfig(config) {
    this.emitConfig(config)
  }

  getSetting (chart) {
    const self = this
    const configObj = self.config

    if (configObj.initialized) {
      configObj.initialized = false
      self.persistConfig(configObj) // should persist w/o state
    }

    // should use `persistConfigImmediatelyWithGridState` in the `setting` panel
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
          self.persistConfigImmediatelyWithGridState(configObj)
        },
        saveTableOption: () => {
          self.persistConfigImmediatelyWithGridState(configObj)
        },
        resetTableOption: () => {
          resetTableOptionConfig(configObj)
          initializeTableConfig(configObj, TABLE_OPTION_SPECS)
          self.persistConfigImmediatelyWithGridState(configObj)
        },
        tableWidgetOnKeyDown: (event, optSpec) => {
          const code = event.keyCode || event.which
          if (code === 13 && isInputWidget(optSpec)) {
            self.persistConfigImmediatelyWithGridState(configObj)
          } else if (code === 13 && event.shiftKey && isTextareaWidget(optSpec)) {
            self.persistConfigImmediatelyWithGridState(configObj)
          }

          event.stopPropagation() /** avoid to conflict with paragraph shortcuts */
        }
      }
    }
  }
}
