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

  if (!config.properties) {
    config.properties = {};
  }
  if (!config.sigma) {
    config.sigma = {
      forceAtlas2: {
        use: true,
        cfg: {
          barnesHutOptimize: true,
          animationDuration: 10000
        }
      },
      noOverlap: {
        use: false,
        cfg: {
          duration: 2000,
          easing: 'quadraticInOut'
        }
      }
    };
  }

  this.targetEl.addClass('network');
  this.sigma = null;
  var NetworkTransformation = zeppelin.NetworkTransformation;
  this.transformation = new NetworkTransformation(config, this);
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
  if (this.config && angular.equals({}, this.config.properties)) {
    this.config.properties = networkData.getNetworkProperties();
  }
  console.log('Render Graph Visualization');
  var containerId = this.targetEl.prop('id');
  var $window = angular.injector(['ng']).get('$window');
  var isFirsTime = this.sigma === null;
  if (isFirsTime) {
    var Sigma = $window.sigma;
    this.sigma = new Sigma({
      renderer: {
        container: containerId,
        type: 'canvas'
      },
      settings: {
        enableEdgeHovering: true,
        maxEdgeSize: 2
      }
    });
    this._attachEvents(Sigma, containerId);
  } else {
    this.sigma.graph.clear();
    this.sigma.stopForceAtlas2();
  }
  networkData.setNodesDefaults(this.config);
  this.sigma.graph.read(networkData.graph);
  this.sigma.refresh();

  this._attachAnimations(isFirsTime);
};

zeppelin.NetworkVisualization.prototype._attachAnimations = function(isFirsTime) {
  console.log('Starting forceAtlas2 & noOverlap events', this.config.sigma);
  var $timeout = angular.injector(['ng']).get('$timeout');
  var _this = this;
  var executeNoverlap = function() {
    var noOverlapDefaults = {
      easing: 'linearNone',
      duration: 2000
    };
    var noOverlapCfg = angular.extend(noOverlapDefaults,
            {});
    if (_this.config.sigma.noOverlap.use && !_this.sigma.isNoverlapRunning()) {
      console.log('starting noOverlap %o', noOverlapCfg);
      if (isFirsTime) {
        _this.sigma.startNoverlap(noOverlapCfg);
      } else {
        _this.sigma.configNoverlap(noOverlapCfg);
        _this.sigma.startNoverlap();
      }
    }
  };
  if (_this.config.sigma.forceAtlas2.use && !_this.sigma.isForceAtlas2Running()) {
    var forceAtlas2Defaults = {
      worker: true,
      barnesHutOptimize: true,
      animationDuration: 10000
    };
    var forceAtlas2Cfg = angular.extend(forceAtlas2Defaults,
            _this.config.sigma.forceAtlas2.cfg);
    console.log('starting forceAtlas2 %o', forceAtlas2Cfg);
    _this.sigma.configForceAtlas2(forceAtlas2Cfg);
    _this.sigma.startForceAtlas2();
    $timeout(function() {
      _this.sigma.stopForceAtlas2();
      executeNoverlap();
    }, forceAtlas2Cfg.animationDuration);
  } else {
    executeNoverlap();
  }
};

zeppelin.NetworkVisualization.prototype._attachEvents = function(Sigma, containerId) {
  var $interpolate = angular.injector(['ng']).get('$interpolate');
  Sigma.plugins.dragNodes(this.sigma, this.sigma.renderers[0]);
  var renderFooterOnClick = function(event) {
    var type = 'node' in event.data ? 'node' : 'edge';
    var entity = event.data[type];
    var footerId = containerId + '_footer';
    var obj = {id: entity.id, label: entity.defaultLabel || entity.label, type: type};
    var html = [$interpolate(['<li><b>{{type}}_id:</b>&nbsp;{{id}}</li>',
                            '<li><b>{{type}}_type:</b>&nbsp;{{label}}</li>'].join(''))(obj)];
    html = html.concat(_.map(entity.data, function(v, k) {
      return $interpolate('<li><b>{{field}}:</b>&nbsp;{{value}}</li>')({field: k, value: v});
    }));
    angular.element('#' + footerId)
      .find('.list-inline')
      .empty()
      .append(html.join(''));
  };
  this.sigma.bind('clickNode clickEdge', renderFooterOnClick);
};

zeppelin.NetworkVisualization.prototype.destroy = function() {
  if (this.sigma) {
    console.log('not destroy sigma visualization');//it kills performances
  }
};

zeppelin.NetworkVisualization.prototype.updateNodeLabel = function(networkData, defaultLabel, value) {
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

zeppelin.NetworkVisualization.prototype.getTransformation = function() {
  return this.transformation;
};

zeppelin.NetworkVisualization.prototype.setConfig = function(config) {
};
