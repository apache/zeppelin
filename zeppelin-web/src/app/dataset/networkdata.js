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

var zeppelin = zeppelin || {};

/**
 * Create table data object from paragraph graph type result
 */
zeppelin.NetworkData = function(graph) {
  zeppelin.TableData.call(this);
  this.graph = graph || {};
  if (this.graph.nodes) {
    this.loadParagraphResult(JSON.stringify({msg: graph, type: zeppelin.DatasetTypes.NETWORK}));
  } else {
    this.comment = [];
    this.columns = [];
    this.rows = [];
  }
};

zeppelin.NetworkData.prototype = Object.create(zeppelin.TableData.prototype);

zeppelin.NetworkData.prototype.loadParagraphResult = function(paragraphResult) {
  if (!paragraphResult || paragraphResult.type !== zeppelin.DatasetTypes.NETWORK) {
    console.log('Can not load paragraph result');
    return;
  }

  this.graph = JSON.parse(paragraphResult.msg.trim() || '{}');

  if (!this.graph.nodes) {
    console.log('Graph result is empty');
    return;
  }

  var columnNames = [];
  var rows = [];
  var comment = '';
  var entities = this.graph.nodes.concat(this.graph.edges);
  var baseColumnNames = [{name: 'id', index: 0, aggr: 'sum'},
                     {name: 'label', index: 1, aggr: 'sum'}];
  var internalFieldsToJump = ['count', 'type', 'size',
                              'data', 'x', 'y', 'color', 'defaultLabel',
                              'labels'];
  var baseCols = _.map(baseColumnNames, function(col) { return col.name; });
  var keys = _.map(entities, function(elem) { return Object.keys(elem.data || {}); });
  keys = _.flatten(keys);
  keys = _.uniq(keys).filter(function(key) {
    return baseCols.indexOf(key) === -1;
  });
  var columnNames = baseColumnNames.concat(_.map(keys, function(elem, i) {
    return {name: elem, index: i + baseColumnNames.length, aggr: 'sum'};
  }));
  for (var i = 0; i < entities.length; i++) {
    var entity = entities[i];
    var col = [];
    var col2 = [];
    entity.data = entity.data || {};
    for (var j = 0; j < columnNames.length; j++) {
      var name = columnNames[j].name;
      var value = name in entity && internalFieldsToJump.indexOf(name) === -1 ?
          entity[name] : entity.data[name];
      var parsedValue = this.parseTableCell(value === null || value === undefined ? '' : value);
      col.push(parsedValue);
      col2.push({key: name, value: parsedValue});
    }
    rows.push(col);
  }

  this.comment = comment;
  this.columns = columnNames;
  this.rows = rows;
};
