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

import Transformation from './transformation';

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
export default class ColumnselectorTransformation extends Transformation {
  constructor(config, columnSelectorProp) {
    super(config);
    this.props = columnSelectorProp;
  }

  getSetting() {
    let self = this;
    let configObj = self.config;
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
        },
      },
    };
  }

  /**
   * Method will be invoked when tableData or config changes
   */
  transform(tableData) {
    this.tableDataColumns = tableData.columns;
    this.removeUnknown();
    return tableData;
  }

  removeUnknown() {
    let fields = this.config;
    for (let f in fields) {
      if (fields[f]) {
        let found = false;
        for (let i = 0; i < this.tableDataColumns.length; i++) {
          let a = fields[f];
          let b = this.tableDataColumns[i];
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
  }
}
