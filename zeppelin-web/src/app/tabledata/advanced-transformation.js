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

import {
  isAggregator, isGroup, isGroupBase, isSingleDimension,
  clearConfig, initializeConfig, removeDuplicatedColumnsInMultiDimensionAxis,
  // groupAndAggregateRows, getGroupAndAggrColumns,
} from './advanced-transformation-util';

const SETTING_TEMPLATE = 'app/tabledata/advanced-transformation-setting.html';

class AdvancedTransformation extends Transformation {
  constructor(config, spec) {
    super(config);

    this.columns = []; /** [{ name, index, comment }] */
    this.props = {};
    this.spec = spec

    initializeConfig(config, spec);
  }

  getSetting() {
    const self = this; /** for closure */
    const configInstance = self.config; /** for closure */

    return {
      template: SETTING_TEMPLATE,
      scope: {
        config: configInstance,
        columns: self.columns,

        getAxisAnnotation: (axisSpec) => {
          return `${axisSpec.name} (${axisSpec.type})`
        },

        getSingleDimensionAxis: (axisSpec) => {
          return configInstance.axis[configInstance.chart.current][axisSpec.name]
        },

        toggleColumnPanel: () => {
          configInstance.panel.columnPanelOpened = !configInstance.panel.columnPanelOpened
          self.emitConfig(configInstance)
        },

        toggleParameterPanel: () => {
          configInstance.panel.parameterPanelOpened = !configInstance.panel.parameterPanelOpened
          self.emitConfig(configInstance)
        },

        clearConfig: () => {
          clearConfig(configInstance)
          initializeConfig(configInstance, self.spec)
          self.emitConfig(configInstance)
        },

        isGroupAxis: (axisSpec) => { return isGroup(axisSpec) },
        isGroupBaseAxis: (axisSpec) => { return isGroupBase(axisSpec) },
        isAggregatorAxis: (axisSpec) => { return isAggregator(axisSpec) },
        isSingleDimensionAxis: (axisSpec) => { return isSingleDimension(axisSpec) },

        parameterChanged: (paramSpec) => { self.emitConfig(configInstance) },
        axisChanged: function(e, ui, axisSpec) {
          removeDuplicatedColumnsInMultiDimensionAxis(configInstance, axisSpec)

          self.emitConfig(configInstance)
        },

        aggregatorChanged: (colIndex, axisSpec, aggregator) => {
          if (isSingleDimension(axisSpec)) {
            configInstance.axis[configInstance.chart.current][axisSpec.name].aggr = aggregator
          } else {
            configInstance.axis[configInstance.chart.current][axisSpec.name][colIndex].aggr = aggregator
          }
          self.emitConfig(configInstance)
        },

        removeFromAxis: function(colIndex, axisSpec) {
          if (isSingleDimension(axisSpec)) {
            configInstance.axis[configInstance.chart.current][axisSpec.name] = null
          } else {
            configInstance.axis[configInstance.chart.current][axisSpec.name].splice(colIndex, 1)
          }
          self.emitConfig(configInstance)
        }
      }
    }
  }

  transform(tableData) {
    this.columns = tableData.columns; /** used in `getSetting` */

    return tableData
  //   const axisSpecs = this.axisSpecs; /** specs */
  //   const axisConfig = this.config.axis; /** configured columns */
  //
  //   const columns = getGroupAndAggrColumns(axisSpecs, axisConfig);
  //   const groupBaseColumns = columns.groupBase;
  //   const groupColumns = columns.group;
  //   const aggregatorColumns = columns.aggregator;
  //   const otherColumns = columns.others;
  //
  //   const grouped = groupAndAggregateRows(tableData.rows, groupBaseColumns, groupColumns, aggregatorColumns)
  //
  //   return {
  //     row: {
  //       all: tableData.rows,
  //       grouped: grouped, /** [ { group<String>, rows<Array>, aggregatedValues<Object> } ] */
  //     },
  //     column: {
  //       all: tableData.columns,
  //       groupBase: groupBaseColumns,
  //       group: groupColumns,
  //       aggregator: aggregatorColumns,
  //       others: otherColumns,
  //     }
  //   }
  }
}

export default AdvancedTransformation
