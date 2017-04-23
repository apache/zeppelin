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
import HandsonHelper from '../../handsontable/handsonHelper'

/**
 * Visualize data in table format
 */
export default class TableVisualization extends Visualization {
  constructor (targetEl, config) {
    super(targetEl, config)
    console.log('Init table viz')
    targetEl.addClass('table')
    this.passthrough = new PassthroughTransformation(config)
  }

  refresh () {
    this.hot.render()
  }

  render (tableData) {
    let height = this.targetEl.height()
    let container = this.targetEl.css('height', height).get(0)
    let resultRows = tableData.rows
    let columnNames = _.pluck(tableData.columns, 'name')
    // eslint-disable-next-line prefer-spread
    let columns = Array.apply(null, Array(tableData.columns.length)).map(function () {
      return {type: 'text'}
    })

    if (this.hot) {
      this.hot.destroy()
    }

    let handsonHelper = new HandsonHelper()
    this.hot = new Handsontable(container, handsonHelper.getHandsonTableConfig(
      columns, columnNames, resultRows))
    this.hot.validateCells(null)
  }

  destroy () {
    if (this.hot) {
      this.hot.destroy()
    }
  }

  getTransformation () {
    return this.passthrough
  }
}
