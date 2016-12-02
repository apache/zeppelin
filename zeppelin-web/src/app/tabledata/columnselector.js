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
 * select columns
 * - columnSelectorProp
 *   [
 *     {
 *       "name":
 *       "tooltip":
 *     },
 *     ...
 *   ]
 */
zeppelin.ColumnselectorTransformation = function(config, columnSelectorProp) {
  zeppelin.Transformation.call(this, config);
  this.props = columnSelectorProp;
};

zeppelin.ColumnselectorTransformation.prototype = Object.create(zeppelin.Transformation.prototype);

zeppelin.ColumnselectorTransformation.prototype.getSetting = function() {
  var self = this;
  var configObj = self.config;
  return {
    template: 'app/tabledata/columnselector_settings.html',
    scope: {
      config: self.config,
      props: self.props,
      tableDataColumns: self.tableDataColumns,
      save: function() {
        self.emitConfig(configObj);
      },
      remove: function(selectorName) {
        configObj[selectorName] = null;
        self.emitConfig(configObj);
      }
    }
  };
};

/**
 * Method will be invoked when tableData or config changes
 */
zeppelin.ColumnselectorTransformation.prototype.transform = function(tableData) {
  this.tableDataColumns = tableData.columns;
  this.removeUnknown();
  return tableData;
};

zeppelin.ColumnselectorTransformation.prototype.removeUnknown = function() {
  var fields = this.config;
  for (var f in fields) {
    if (fields[f]) {
      var found = false;
      for (var i = 0; i < this.tableDataColumns.length; i++) {
        var a = fields[f];
        var b = this.tableDataColumns[i];
        if (a.index === b.index && a.name === b.name) {
          found = true;
          break;
        }
      }
      if (!found && (fields[f] instanceof Object) && !(fields[f] instanceof Array)) {
        fields[f] = null;
      }
    }
  }
};
