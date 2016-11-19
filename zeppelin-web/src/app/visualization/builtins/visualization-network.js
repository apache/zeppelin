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
zeppelin.NetworkVisualization = function(targetEl, config) {
  zeppelin.Visualization.call(this, targetEl, config);
  console.log('Init network viz');
  this.targetEl.addClass('network');
  this.sigma = null;
};

zeppelin.NetworkVisualization.prototype = Object.create(zeppelin.Visualization.prototype);

zeppelin.NetworkVisualization.prototype.refresh = function() {
  this.sigma.refresh();
};

zeppelin.NetworkVisualization.prototype.render = function(networkData) {
  if (!('graph' in networkData)) {
    console.log('graph not found');
    return;
  }
  console.log('Render Graph Visualization');
  var containerId = this.targetEl.prop('id');
  var $window = angular.injector(['ng']).get('$window');
  var $timeout = angular.injector(['ng']).get('$timeout');
  if (!this.sigma) {
    var Sigma = $window.sigma;
    this.sigma = new Sigma({
      renderer: {
        container: containerId,
        type: 'canvas'
      },
      settings: {
        enableEdgeHovering: true,
        minNodeSize: 0,
        maxNodeSize: 0,
        minEdgeSize: 0,
        maxEdgeSize: 0
      }
    });
    this.attachEvents(Sigma, containerId);
  } else {
    this.sigma.graph.clear();
    this.sigma.stopForceAtlas2();
  }
  networkData.setNodesDefaults(this.config);
  this.sigma.graph.read(networkData.graph);
  this.sigma.refresh();

  if (angular.equals({}, this.config.network.nodes)) {
    console.log('Starting forceAtlas2 & noOverlap events');
    var nodesLength = networkData.graph.nodes.length;
    var isBigGraph = nodesLength > 50;
    var forceAtlas2Config = {worker: true, barnesHutOptimize: nodesLength > 50, slowDown: 0.5};
    var _this = this;
    this.sigma.startForceAtlas2(forceAtlas2Config);
    $timeout(function() {
      _this.sigma.stopForceAtlas2();
      _this.sigma.startNoverlap();
    }, isBigGraph ? 7000 : 1000);
  }
};

zeppelin.NetworkVisualization.prototype.attachEvents = function(Sigma, containerId) {
  var $interpolate = angular.injector(['ng']).get('$interpolate');
  var _this = this;
  var dragListener = Sigma.plugins.dragNodes(this.sigma, this.sigma.renderers[0]);
  dragListener.bind('drop', function(event) {
    var nodeId = event.data.node.id;
    _this.config.network.nodes[nodeId] = {
      x: event.data.node.x,
      y: event.data.node.y
    };
  });
  var renderFooterOnClick = function(event) {
    var type = 'node' in event.data ? 'node' : 'edge';
    var entity = event.data[type];
    var footerId = containerId.match(/[0-9]+-[0-9]+_[0-9]+/ig)[0];
    var obj = {id: entity.id, label: entity.defaultLabel || entity.label, type: type};
    var html = [$interpolate(['<li><b>{{type}}_id:</b>&nbsp;{{id}}</li>',
                            '<li><b>{{type}}_type:</b>&nbsp;{{label}}</li>'].join(''))(obj)];
    html = html.concat(_.map(entity.data, function(v, k) {
      return $interpolate('<li><b>{{field}}:</b>&nbsp;{{value}}</li>')({field: k, value: v});
    }));
    angular.element('#' + footerId + '_network_footer')
      .find('.list-inline')
      .empty()
      .append(html.join(''));
  };
  this.sigma.bind('clickNode clickEdge', renderFooterOnClick);
  var nooverlapConf = {
    easing: 'quadraticInOut',
    duration: 2000
  };
  var overlapListener = this.sigma.configNoverlap(nooverlapConf);
  overlapListener.bind('stop', function(event) {
    _this.sigma.graph.nodes()
      .forEach(function(node) {
        _this.config.network.nodes[node.id] = {
          x: node.x,
          y: node.y
        };
      });
  });
};

zeppelin.NetworkVisualization.prototype.destroy = function() {
  if (this.sigma) {
    console.log('not destroy sigma visualization');//it kills performances
  }
};

zeppelin.NetworkVisualization.prototype.updateNodeLabel = function(networkData, defaultLabel, value) {
  networkData.updateNodeLabel(defaultLabel, value);
  this.sigma.graph.nodes()
    .filter(function(node) {
      return node.defaultLabel === defaultLabel;
    })
    .forEach(function(node) {
      node.label = (value === 'label' ? defaultLabel : value in node ? node[value] : node.data[value]) + '';
    });
  this.sigma.refresh();
  angular.extend(networkData.graph, {nodes: this.sigma.graph.nodes()});
};
