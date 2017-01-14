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
import HandsonHelper from '../../handsontable/handsonHelper';

/**
 * Visualize data in table format
 */
export default class TableVisualization extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config);
    console.log('Init table viz');
    targetEl.addClass('table');
    this.passthrough = new PassthroughTransformation(config);
  };

  refresh() {
    this.hot.render();
  };

  render(tableData) {
    var height = this.targetEl.height();
    var container = this.targetEl.css('height', height).get(0);
    var resultRows = tableData.rows;
    var columnNames = _.pluck(tableData.columns, 'name');

    if (this.hot) {
      this.hot.destroy();
    }

    if (!this.columns) {
      this.columns = Array.apply(null, Array(tableData.columns.length)).map(function() {
        return {type: 'text'};
      });
    }

    var handsonHelper = new HandsonHelper();

    this.hot = new Handsontable(container, handsonHelper.getHandsonTableConfig(
      this.columns, columnNames, resultRows));
    this.hot.validateCells(null);
  };

  destroy() {
    if (this.hot) {
      this.hot.destroy();
    }
  };

  getTransformation() {
    return this.passthrough;
  };
}
