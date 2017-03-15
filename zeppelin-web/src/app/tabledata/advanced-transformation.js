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
  isAggregator, isGroup, isKey, isSingleDimension,
  clearConfig, initializeConfig,
  removeDuplicatedColumnsInMultiDimensionAxis, applyMaxAxisCount,
  getCubeWithSchema, getColumnsFromAxis,
} from './advanced-transformation-util';

import {
  getCurrentChart,
  getCurrentChartAxis,
  getCurrentChartAxisSpecs,
  getCurrentChartParam,
} from './advanced-transformation-api'

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
          let anno = `${axisSpec.name}`
          if (axisSpec.valueType) {
            anno = `${anno} (${axisSpec.valueType})`
          }

          return anno
        },

        getAxisTypeAnnotation: (axisSpec) => {
          let anno = `${axisSpec.axisType}`
          if (typeof axisSpec.maxAxisCount !== "undefined") {
            anno = `${anno} (${axisSpec.maxAxisCount})`
          }

          return anno
        },

        getAxisTypeAnnotationColor: (axisSpec) => {
          if (isAggregator(axisSpec)) {
            return { 'background-color': '#5782bd' };
          } else if (isGroup(axisSpec)) {
            return { 'background-color': '#cd5c5c' };
          } else if (isKey(axisSpec)) {
            return { 'background-color': '#906ebd' };
          }
        },

        getSingleDimensionAxis: (axisSpec) => {
          return getCurrentChartAxis(configInstance)[axisSpec.name]
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
        isKeyAxis: (axisSpec) => { return isKey(axisSpec) },
        isAggregatorAxis: (axisSpec) => { return isAggregator(axisSpec) },
        isSingleDimensionAxis: (axisSpec) => { return isSingleDimension(axisSpec) },

        chartChanged: (selected) => {
          configInstance.chart.current = selected
          self.emitConfig(configInstance)
        },

        parameterChanged: (paramSpec) => {
          self.emitConfig(configInstance)
        },

        axisChanged: function(e, ui, axisSpec) {
          removeDuplicatedColumnsInMultiDimensionAxis(configInstance, axisSpec)
          applyMaxAxisCount(configInstance, axisSpec)
          self.emitConfig(configInstance)
        },

        aggregatorChanged: (colIndex, axisSpec, aggregator) => {
          if (isSingleDimension(axisSpec)) {
            getCurrentChartAxis(configInstance)[axisSpec.name].aggr = aggregator
          } else {
            getCurrentChartAxis(configInstance)[axisSpec.name][colIndex].aggr = aggregator
          }
          self.emitConfig(configInstance)
        },

        removeFromAxis: function(colIndex, axisSpec) {
          if (isSingleDimension(axisSpec)) {
            getCurrentChartAxis(configInstance)[axisSpec.name] = null
          } else {
            getCurrentChartAxis(configInstance)[axisSpec.name].splice(colIndex, 1)
          }
          self.emitConfig(configInstance)
        }
      }
    }
  }

  transform(tableData) {
    this.columns = tableData.columns; /** used in `getSetting` */

    const conf = this.config
    const chart = getCurrentChart(conf)
    const axis = getCurrentChartAxis(conf)
    const param = getCurrentChartParam(conf)
    const axisSpecs = getCurrentChartAxisSpecs(conf)

    const columns = getColumnsFromAxis(axisSpecs, axis);
    const keyColumns = columns.key;
    const groupColumns = columns.group;
    const aggregatorColumns = columns.aggregator;

    const { cube, schema, } =
      getCubeWithSchema(tableData.rows, keyColumns, groupColumns, aggregatorColumns);

    return {
      chart: chart, /** current chart */
      axis: axis, /** persisted axis */
      parameter: param, /** persisted parameter */

      cube: cube, /** multi-dimensional data cube */
      schema: schema, /** schema for key, group, aggr info */
    }
  }
}

export default AdvancedTransformation
