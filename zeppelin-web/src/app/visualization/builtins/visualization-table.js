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
      fastWatch: true,
      enableGroupHeaderSelection: true,
      treeRowHeaderAlwaysVisible: false,
      columnDefs: columnNames.map(colName => {
        return {
          name: colName,
          type: TableColumnType.STRING,
        }
      }),
      rowEditWaitInterval: -1, /** disable saveRow event */
      onRegisterApi: onRegisterApiCallback,
    }

    this.setDynamicGridOptions(gridOptions, config)
    this.addColumnMenus(gridOptions)

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
      const scope = this.targetEl.scope()
      const gridApiId = this.getGridApiId()
      scope[gridApiId].core.notifyDataChange(this._uiGridConstants.dataChange.ALL)
    }
  }

  refreshGridColumn() {
    const gridElemId = this.getGridElemId()
    const gridElem = angular.element(`#${gridElemId}`)

    if (gridElem) {
      const scope = this.targetEl.scope()
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
              ui-grid-exporter></div>`)

      gridElem.css('height', this.targetEl.height() - 10)
      gridElem = this._compile(gridElem)(this.targetEl.scope())
      this.targetEl.append(gridElem)

      const scope = this.targetEl.scope()

      // set gridApi for this elem
      const gridApiId = this.getGridApiId()
      const onRegisterApiCallback = (gridApi) => { scope[gridApiId] = gridApi }

      // set gridOptions for this elem
      const gridOptions = this.createGridOptions(tableData, onRegisterApiCallback, config)
      this.targetEl.scope()[gridElemId] = gridOptions
    } else {
      // don't need to update gridOptions.data since it's synchronized by paragraph execution
      const scope = this.targetEl.scope()
      this.setDynamicGridOptions(scope[gridElemId], config)
      this.refreshGrid()
    }
  }

  destroy () {
  }

  getTransformation () {
    return this.passthrough
  }

  getSetting (chart) {
    const self = this
    const configObj = self.config

    if (configObj.initialized) {
      configObj.initialized = false
      self.emitConfig(configObj)
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
        tableOptionChanged: () => {
          this.emitConfig(configObj)
        },
        saveTableOption: () => {
          this.emitConfig(configObj)
        },
        resetTableOption: () => {
          resetTableOptionConfig(configObj)
          initializeTableConfig(configObj, TABLE_OPTION_SPECS)
          this.emitConfig(configObj)
        },
        optionWidgetOnKeyDown: (event, optSpec) => {
          const code = event.keyCode || event.which
          if (code === 13 && isInputWidget(optSpec)) {
            self.emitConfig(configObj)
          } else if (code === 13 && event.shiftKey && isTextareaWidget(optSpec)) {
            self.emitConfig(configObj)
          }

          event.stopPropagation() /** avoid to conflict with paragraph shortcuts */
        }
      }
    }
  }
}
