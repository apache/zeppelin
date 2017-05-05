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

/**
 * Visualize data in table format
 */
export default class TableVisualization extends Visualization {
  constructor (targetEl, config) {
    super(targetEl, config)
    this.passthrough = new PassthroughTransformation(config)
  }

  refresh () {
  }

  createGridOptions(tableData) {
    const rows = tableData.rows
    const columnNames = tableData.columns.map(c => c.name)

    const gridData = rows.map(r => {
      return columnNames.reduce((acc, colName, index) => {
        acc[colName] = r[index]
        return acc
      }, {})
    })

    return {
      data: gridData,
      modifierKeysToMultiSelectCells: true,
      enableFiltering: true,
      exporterMenuCsv: false,
      columnDefs: columnNames.map(colName => {
        return {
          name: colName,
        }
      }),
      enableGridMenu: true,
    }
  }

  render (tableData) {
    // angular doesn't allow `-` in scope variable name
    const gridElemId = `${this.targetEl[0].id}_grid`.replace('-', '_')

    let gridElem = document.getElementById(gridElemId)

    const gridOptions = this.createGridOptions(tableData)

    this.targetEl.scope()[gridElemId] = gridOptions

    if (!gridElem) {
      gridElem = angular.element(
        `<div id="${gridElemId}" ui-grid="${gridElemId}" 
              ui-grid-edit ui-grid-row-edit ui-grid-selection 
              ui-grid-cellNav ui-grid-pinning
              ui-grid-empty-base-layer ui-grid-auto-resize
              ui-grid-resize-columns ui-grid-move-columns
              ui-grid-exporter></div>`
      )
      gridElem = this._compile(gridElem)(this.targetEl.scope())
      this.targetEl.append(gridElem)
    }
  }

  destroy () {
  }

  getTransformation () {
    return this.passthrough
  }

  getSetting (chart) {
    let self = this
    let configObj = self.config

    return {
      template: `
        <div>
            Hello
        </div>`,
      scope: {
        config: configObj,
        save: function () {
          self.emitConfig(configObj)
        }
      }
    }
  }
}
