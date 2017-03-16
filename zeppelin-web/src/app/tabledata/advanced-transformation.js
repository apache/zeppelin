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
  getCurrentChart, getCurrentChartAxis, getCurrentChartAxisSpecs, getCurrentChartParam,
  isAggregatorAxis, isGroupAxis, isKeyAxis, isSingleDimensionAxis,
  clearConfig, initializeConfig,
  removeDuplicatedColumnsInMultiDimensionAxis, applyMaxAxisCount, getColumnsFromAxis,
  getTransformer,
  isInputWidget, isOptionWidget, isCheckboxWidget, isTextareaWidget,
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

        clearConfig: () => {
          clearConfig(configInstance)
          initializeConfig(configInstance, self.spec)
          self.emitConfig(configInstance)
        },

        toggleColumnPanel: () => {
          configInstance.panel.columnPanelOpened = !configInstance.panel.columnPanelOpened
          self.emitConfig(configInstance)
        },

        toggleParameterPanel: () => {
          configInstance.panel.parameterPanelOpened = !configInstance.panel.parameterPanelOpened
          self.emitConfig(configInstance)
        },

        getAxisAnnotation: (axisSpec) => {
          let anno = `${axisSpec.name}`
          if (axisSpec.valueType) {
            anno = `${anno} (${axisSpec.valueType})`
          }

          return anno
        },

        getAxisTypeAnnotation: (axisSpec) => {
          let anno = `${axisSpec.axisType}`
          if (typeof axisSpec.maxAxisCount !== 'undefined') {
            anno = `${anno} (${axisSpec.maxAxisCount})`
          }

          return anno
        },

        getAxisTypeAnnotationColor: (axisSpec) => {
          if (isAggregatorAxis(axisSpec)) {
            return { 'background-color': '#5782bd' };
          } else if (isGroupAxis(axisSpec)) {
            return { 'background-color': '#cd5c5c' };
          } else if (isKeyAxis(axisSpec)) {
            return { 'background-color': '#906ebd' };
          } else {
            return { 'background-color': '#62bda9' };
          }
        },

        isGroupAxis: (axisSpec) => { return isGroupAxis(axisSpec) },
        isKeyAxis: (axisSpec) => { return isKeyAxis(axisSpec) },
        isAggregatorAxis: (axisSpec) => { return isAggregatorAxis(axisSpec) },
        isSingleDimensionAxis: (axisSpec) => { return isSingleDimensionAxis(axisSpec) },
        getSingleDimensionAxis: (axisSpec) => { return getCurrentChartAxis(configInstance)[axisSpec.name] },

        chartChanged: (selected) => {
          configInstance.chart.current = selected
          self.emitConfig(configInstance)
        },

        axisChanged: function(e, ui, axisSpec) {
          removeDuplicatedColumnsInMultiDimensionAxis(configInstance, axisSpec)
          applyMaxAxisCount(configInstance, axisSpec)
          self.emitConfig(configInstance)
        },

        aggregatorChanged: (colIndex, axisSpec, aggregator) => {
          if (isSingleDimensionAxis(axisSpec)) {
            getCurrentChartAxis(configInstance)[axisSpec.name].aggr = aggregator
          } else {
            getCurrentChartAxis(configInstance)[axisSpec.name][colIndex].aggr = aggregator
          }
          self.emitConfig(configInstance)
        },

        removeFromAxis: function(colIndex, axisSpec) {
          if (isSingleDimensionAxis(axisSpec)) {
            getCurrentChartAxis(configInstance)[axisSpec.name] = null
          } else {
            getCurrentChartAxis(configInstance)[axisSpec.name].splice(colIndex, 1)
          }
          self.emitConfig(configInstance)
        },

        isInputWidget: function(paramSpec) { return isInputWidget(paramSpec) },
        isCheckboxWidget: function(paramSpec) { return isCheckboxWidget(paramSpec) },
        isOptionWidget: function(paramSpec) { return isOptionWidget(paramSpec) },
        isTextareaWidget: function(paramSpec) { return isTextareaWidget(paramSpec) },

        parameterChanged: (paramSpec) => {
          self.emitConfig(configInstance)
        },

        keyEventOnDynamicParameter: (event) => {
          if (event.which === 13 || event.keyCode === 13) {
            /** enter */
            self.emitConfig(configInstance)
          }
        },
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
    const customColumns = columns.custom

    let transformer = getTransformer(conf, tableData.rows, keyColumns, groupColumns, aggregatorColumns)

    return {
      chart: chart, /** current chart */
      axis: axis, /** persisted axis */
      parameter: param, /** persisted parameter */
      column: {
        key: keyColumns, group: groupColumns, aggregator: aggregatorColumns, custom: customColumns,
      },

      transformer: transformer,
    }
  }
}

export default AdvancedTransformation
