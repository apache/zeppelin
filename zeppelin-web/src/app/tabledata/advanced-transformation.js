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
  groupAndAggregateRows, getGroupAndAggrColumns,
} from './advanced-transformation-util';

const SETTING_TEMPLATE = 'app/tabledata/advanced-transformation-setting.html';

class AdvancedTransformation extends Transformation {
  constructor(config, spec) {
    super(config);

    this.columns = []; /** [{ name, index, comment }] */
    this.props = {};

    /**
     * spec.axis: [{ name, dimension, type, aggregator, group }]
     * spec.parameter: [{ name, type, defaultValue, description }]
     *
     * add the `name` field while converting to array to easily manipulate
     */
    const axisSpecs = [];
    for (let name in spec.axis) {
      const axisSpec = spec.axis[name];
      axisSpec.name = name;
      axisSpecs.push(axisSpec);
    }
    this.axisSpecs = axisSpecs;

    const paramSpecs = [];
    for (let name in spec.parameter) {
      const parameterSpec = spec.parameter[name];
      parameterSpec.name = name;
      paramSpecs.push(parameterSpec);
    }
    this.paramSpecs = paramSpecs;

    /** initialize config.axis */
    if (!this.config.axis) { this.config.axis = {}; }
    for (let i = 0; i < axisSpecs.length; i++) {
      const axisSpec = axisSpecs[i];
      const persistedConfig = this.config.axis[axisSpec.name];

      // // behavior of jqyoui-element depends on its model (ng-model)
      // // so, we have to initialize its underlying ng-model to array if it's not array
      if (!isSingleDimension(axisSpec) && !Array.isArray(persistedConfig)) {
        this.config.axis[axisSpec.name] = [];
      } else if (isSingleDimension(axisSpec) && Array.isArray(persistedConfig)) {
        this.config.axis[axisSpec.name] = {};
      }
    }

    /** initialize config.parameter*/
    if (!this.config.parameter) { this.config.parameter = {}; }
    for (let i = 0; i < paramSpecs.length; i++) {
      const paramSpec = paramSpecs[i];
      if (!this.config.parameter[paramSpec.name]) {
        this.config.parameter[paramSpec.name] = paramSpec.defaultValue;
      }
    }

    /** initialize config.panel */
    if (!this.config.panel) {
      this.config.panel = { columnPanelOpened: true, parameterPanelOpened: true, };
    }
  }

  getSetting() {
    const self = this; /** for closure */
    /**
     * config: { axis, parameter }
     */
    const configInstance = self.config; /** for closure */

    return {
      template: SETTING_TEMPLATE,
      scope: {
        config: configInstance,
        columns: self.columns,
        axisSpecs: self.axisSpecs,
        paramSpecs: self.paramSpecs,

        getAxisAnnotation: (axisSpec) => {
          return `${axisSpec.name} (${axisSpec.type})`
        },

        toggleColumnPanel: () => {
          configInstance.panel.columnPanelOpened =
            !configInstance.panel.columnPanelOpened
          self.emitConfig(configInstance)
        },

        toggleParameterPanel: () => {
          configInstance.panel.parameterPanelOpened =
            !configInstance.panel.parameterPanelOpened
          self.emitConfig(configInstance)
        },

        isGroupAxis: (axisSpec) => { return isGroup(axisSpec) },
        isGroupBaseAxis: (axisSpec) => { return isGroupBase(axisSpec) },
        isAggregatorAxis: (axisSpec) => { return isAggregator(axisSpec) },
        isSingleDimensionAxis: (axisSpec) => { return isSingleDimension(axisSpec) },

        parameterChanged: (paramSpec) => {
          self.emitConfig(configInstance)
        },

        aggregatorChanged: (colIndex, axisName, aggregator) => {
          configInstance.axis[axisName][colIndex].aggr = aggregator
          self.emitConfig(configInstance)
        },

        axisChanged: function(e, ui, axisSpec) {
          self.emitConfig(configInstance)
        },

        removeFromSingleDimension: function(axisName) {
          configInstance.axis[axisName] = null
          self.emitConfig(configInstance)
        },

        removeFromMultipleDimension: function(colIndex, axisName) {
          configInstance.axis[axisName].splice(colIndex, 1)
          self.emitConfig(configInstance)
        },
      }
    }
  }

  transform(tableData) {
    this.columns = tableData.columns; /** used in `getSetting` */
    const axisSpecs = this.axisSpecs; /** specs */
    const axisConfig = this.config.axis; /** configured columns */

    const {
        groupColumns, aggregatedColumns, normalColumns
      } = getGroupAndAggrColumns(axisSpecs, axisConfig);

    const grouped = groupAndAggregateRows(tableData.rows, groupColumns, aggregatedColumns)

    return {
      raw: tableData.rows,
      grouped: grouped, /** [{ group, groupedRows, aggregated }] */
      column: {
        allColumns: tableData.columns,
        groupColumns: groupColumns,
        aggregatedColumns,
        normalColumns: normalColumns,
      }
    }
  }
}

export default AdvancedTransformation
