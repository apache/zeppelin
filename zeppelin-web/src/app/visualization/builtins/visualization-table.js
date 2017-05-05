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
    targetEl.addClass('table')
    this.passthrough = new PassthroughTransformation(config)
  }

  refresh () {
  }

  render (tableData) {
    const height = this.targetEl.height()
    const container = this.targetEl.css('height', height).get(0)

    const rows = tableData.rows
    const columnNames = tableData.columns.map(c => c.name)

    // eslint-disable-next-line prefer-spread
    // let columns = Array.apply(null, Array(tableData.columns.length)).map(function () {
    //   return {type: 'text'}
    // })

    console.log(container)
    console.log(rows)
    console.log(columnNames)
  }

  destroy () {
  }

  getTransformation () {
    return this.passthrough
  }
}
