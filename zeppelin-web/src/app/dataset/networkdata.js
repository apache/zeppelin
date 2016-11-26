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
  this.isNewInstance = true;
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

  this.setNodesDefaults();
  this.setEdgesDefaults();

  var columnNames = [];
  var rows = [];
  var comment = '';
  var entities = this.graph.nodes.concat(this.graph.edges);
  var baseColumnNames = [{name: 'id', index: 0, aggr: 'sum'},
                     {name: 'defaultLabel', index: 1, aggr: 'sum'}];
  var internalFieldsToJump = ['count', 'type', 'size', 'label',
                              'data', 'x', 'y', 'color',
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

zeppelin.NetworkData.prototype.updateNodeLabel = function(defaultLabel, labelField) {
  this.graph.nodes
    .filter(function(node) {
      return node.defaultLabel === defaultLabel;
    })
    .forEach(function(node) {
      node.label = (labelField === 'label' ?
              defaultLabel : labelField in node ? node[labelField] : node.data[labelField]) + '';
    });
};

zeppelin.NetworkData.prototype.setNodesDefaults = function(config) {
  console.log('setting nodes defaults');
  this.graph.nodes
    .forEach(function(node) {
      node.defaultLabel = node.defaultLabel || node.label || '';
      var properties = config ? config.network.properties[node.defaultLabel] || {} : {};
      var selected = properties.selected || 'id';
      node.label = (selected in node ? node[selected] : node.data[selected]) + '';
      /*if (config && node.id in config.network.nodes) {
        node.x = config.network.nodes[node.id].x;
        node.y = config.network.nodes[node.id].y;
      } else {
        node.x = node.x || Math.random();
        node.y = node.y || Math.random();
      }*/
      var isPresent = config && node.id in config.network.nodes ? true : false;
      node.x = isPresent ? config.network.nodes[node.id].x : Math.random();
      node.y = isPresent ? config.network.nodes[node.id].y : Math.random();
      node.size = node.size || 10;
    });
};

zeppelin.NetworkData.prototype.setEdgesDefaults = function(config) {
  console.log('setting edges defaults');
  this.graph.edges
    .forEach(function(edge) {
      edge.size = edge.size || 4;
      edge.type = edge.type || 'arrow';
      edge.color = edge.color || '#D3D3D3';
      edge.count = edge.count || 1;
      edge.defaultLabel = edge.defaultLabel || edge.label || '';
    });
};
