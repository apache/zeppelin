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
 * pivot table data and return d3 chart data
 */
zeppelin.NetworkTransformation = function(config, viz) {
  zeppelin.Transformation.call(this, config);
  this.viz = viz;
};

zeppelin.NetworkTransformation.prototype = Object.create(zeppelin.Transformation.prototype);

zeppelin.NetworkTransformation.prototype.getSetting = function() {
  var self = this;
  var configObj = self.viz.config;
  return {
    template: 'app/tabledata/network_settings.html',
    scope: {
      config: configObj,
      easingOpts: [{value: 'linearNone', label: 'linearNone'},
                   {value: 'quadraticIn', label: 'quadraticIn'},
                   {value: 'quadraticOut', label: 'quadraticOut'},
                   {value: 'quadraticInOut', label: 'quadraticInOut'},
                   {value: 'cubicIn', label: 'cubicIn'},
                   {value: 'cubicOut', label: 'cubicOut'},
                   {value: 'cubicInOut', label: 'cubicInOut'}],
      isEmptyObject: function(obj) {
        obj = obj || {};
        return angular.equals(obj, {});
      },
      setNetworkLabel: function(defaultLabel, value) {
        configObj.properties[defaultLabel].selected = value;
        self.viz.updateNodeLabel(self.networkData, defaultLabel, value);
      },
      saveConfig: function() {
        self.emitConfig(configObj);
      }
    }
  };
};

zeppelin.NetworkTransformation.prototype.transform = function(networkData) {
  this.networkData;
  return networkData;
};

