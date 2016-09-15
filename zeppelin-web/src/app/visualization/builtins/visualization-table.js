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

'use strict';

/**
 * Visualize data in table format
 */
zeppelin.TableVisualization = function(targetEl) {
  zeppelin.Visualization.call(this, targetEl);
  console.log('Init table viz');
  targetEl.addClass('table');
};

zeppelin.TableVisualization.prototype = Object.create(zeppelin.Visualization);

zeppelin.TableVisualization.prototype.render = function(tableData) {
  var height = this.targetEl.height();
  var container = this.targetEl.css('height', height).get(0);
  var resultRows = tableData.rows;
  var columnNames = _.pluck(tableData.columnNames, 'name');

  if (this.hot) {
    this.hot.destroy();
  }

  this.hot = new Handsontable(container, {
    colHeaders: columnNames,
    data: resultRows,
    rowHeaders: false,
    stretchH: 'all',
    sortIndicator: true,
    columnSorting: true,
    contextMenu: false,
    manualColumnResize: true,
    manualRowResize: true,
    readOnly: true,
    readOnlyCellClassName: '',  // don't apply any special class so we can retain current styling
    fillHandle: false,
    fragmentSelection: true,
    disableVisualSelection: true,
    cells: function(row, col, prop) {
      var cellProperties = {};
      cellProperties.renderer = function(instance, td, row, col, prop, value, cellProperties) {
        if (value instanceof moment) {
          td.innerHTML = value._i;
        } else if (!isNaN(value)) {
          cellProperties.format = '0,0.[00000]';
          td.style.textAlign = 'left';
          Handsontable.renderers.NumericRenderer.apply(this, arguments);
        } else if (value.length > '%html'.length && '%html ' === value.substring(0, '%html '.length)) {
          td.innerHTML = value.substring('%html'.length);
        } else {
          Handsontable.renderers.TextRenderer.apply(this, arguments);
        }
      };
      return cellProperties;
    }
  });
};

zeppelin.TableVisualization.prototype.destroy = function() {
  if (this.hot) {
    this.hot.destroy();
  }
};
