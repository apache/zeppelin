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
zeppelin.TableVisualization = function(targetEl, config) {
  zeppelin.Visualization.call(this, targetEl, config);
  console.log('Init table viz');
  targetEl.addClass('table');
  var PassthroughTransformation = zeppelin.PassthroughTransformation;
  this.passthrough = new PassthroughTransformation(config);
};

zeppelin.TableVisualization.prototype = Object.create(zeppelin.Visualization.prototype);

zeppelin.TableVisualization.prototype.refresh = function() {
  this.hot.render();
};

zeppelin.TableVisualization.prototype.render = function(tableData) {
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

  var handsonHelper = new zeppelin.HandsonHelper();

  this.hot = new Handsontable(container, handsonHelper.getHandsonTableConfig(
    this.columns, columnNames, resultRows));
  this.hot.validateCells(null);
};

zeppelin.TableVisualization.prototype.destroy = function() {
  if (this.hot) {
    this.hot.destroy();
  }
};

zeppelin.TableVisualization.prototype.getTransformation = function() {
  return this.passthrough;
};
