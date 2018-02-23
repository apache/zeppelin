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
  getCurrentChart, getCurrentChartAxis, getCurrentChartParam,
  serializeSharedAxes, useSharedAxis,
  getCurrentChartAxisSpecs, getCurrentChartParamSpecs,
  initializeConfig, resetAxisConfig, resetParameterConfig,
  isAggregatorAxis, isGroupAxis, isKeyAxis, isSingleDimensionAxis,
  removeDuplicatedColumnsInMultiDimensionAxis, applyMaxAxisCount,
  isInputWidget, isOptionWidget, isCheckboxWidget, isTextareaWidget, parseParameter,
  getTransformer,
} from './advanced-transformation-util';

const SETTING_TEMPLATE = 'app/tabledata/advanced-transformation-setting.html';

export default class AdvancedTransformation extends Transformation {
  constructor(config, spec) {
    super(config);

    this.columns = []; /** [{ name, index, comment }] */
    this.props = {};
    this.spec = spec;

    initializeConfig(config, spec);
  }

  emitConfigChange(conf) {
    conf.chartChanged = false;
    conf.parameterChanged = false;
    this.emitConfig(conf);
  }

  emitChartChange(conf) {
    conf.chartChanged = true;
    conf.parameterChanged = false;
    this.emitConfig(conf);
  }

  emitParameterChange(conf) {
    conf.chartChanged = false;
    conf.parameterChanged = true;
    this.emitConfig(conf);
  }

  getSetting() {
    const self = this; /** for closure */
    const configInstance = self.config; /** for closure */

    if (self.spec.initialized) {
      self.spec.initialized = false;
      self.emitConfig(configInstance);
    }

    return {
      template: SETTING_TEMPLATE,
      scope: {
        config: configInstance,
        columns: self.columns,
        resetAxisConfig: () => {
          resetAxisConfig(configInstance);
          self.emitChartChange(configInstance);
        },

        resetParameterConfig: () => {
          resetParameterConfig(configInstance);
          self.emitParameterChange(configInstance);
        },

        toggleColumnPanel: () => {
          configInstance.panel.columnPanelOpened = !configInstance.panel.columnPanelOpened;
          self.emitConfigChange(configInstance);
        },

        toggleParameterPanel: () => {
          configInstance.panel.parameterPanelOpened = !configInstance.panel.parameterPanelOpened;
          self.emitConfigChange(configInstance);
        },

        getAxisAnnotation: (axisSpec) => {
          let anno = `${axisSpec.name}`;
          if (axisSpec.valueType) {
            anno = `${anno} (${axisSpec.valueType})`;
          }

          return anno;
        },

        getAxisTypeAnnotation: (axisSpec) => {
          let anno = '';

          let minAxisCount = axisSpec.minAxisCount;
          let maxAxisCount = axisSpec.maxAxisCount;

          if (isSingleDimensionAxis(axisSpec)) {
            maxAxisCount = 1;
          }

          let comment = '';
          if (minAxisCount) {
            comment = `min: ${minAxisCount}`;
          }
          if (minAxisCount && maxAxisCount) {
            comment = `${comment}, `;
          }
          if (maxAxisCount) {
            comment = `${comment}max: ${maxAxisCount}`;
          }

          if (comment !== '') {
            anno = `${anno} (${comment})`;
          }

          return anno;
        },

        getAxisAnnotationColor: (axisSpec) => {
          if (isAggregatorAxis(axisSpec)) {
            return {'background-color': '#5782bd'};
          } else if (isGroupAxis(axisSpec)) {
            return {'background-color': '#cd5c5c'};
          } else if (isKeyAxis(axisSpec)) {
            return {'background-color': '#906ebd'};
          } else {
            return {'background-color': '#62bda9'};
          }
        },

        useSharedAxis: (chartName) => {
          return useSharedAxis(configInstance, chartName);
        },
        isGroupAxis: (axisSpec) => {
          return isGroupAxis(axisSpec);
        },
        isKeyAxis: (axisSpec) => {
          return isKeyAxis(axisSpec);
        },
        isAggregatorAxis: (axisSpec) => {
          return isAggregatorAxis(axisSpec);
        },
        isSingleDimensionAxis: (axisSpec) => {
          return isSingleDimensionAxis(axisSpec);
        },
        getSingleDimensionAxis: (axisSpec) => {
          return getCurrentChartAxis(configInstance)[axisSpec.name];
        },

        chartChanged: (selected) => {
          configInstance.chart.current = selected;
          self.emitChartChange(configInstance);
        },

        axisChanged: function(e, ui, axisSpec) {
          removeDuplicatedColumnsInMultiDimensionAxis(configInstance, axisSpec);
          applyMaxAxisCount(configInstance, axisSpec);

          self.emitChartChange(configInstance);
        },

        aggregatorChanged: (colIndex, axisSpec, aggregator) => {
          if (isSingleDimensionAxis(axisSpec)) {
            getCurrentChartAxis(configInstance)[axisSpec.name].aggr = aggregator;
          } else {
            getCurrentChartAxis(configInstance)[axisSpec.name][colIndex].aggr = aggregator;
            removeDuplicatedColumnsInMultiDimensionAxis(configInstance, axisSpec);
          }

          self.emitChartChange(configInstance);
        },

        removeFromAxis: function(colIndex, axisSpec) {
          if (isSingleDimensionAxis(axisSpec)) {
            getCurrentChartAxis(configInstance)[axisSpec.name] = null;
          } else {
            getCurrentChartAxis(configInstance)[axisSpec.name].splice(colIndex, 1);
          }

          self.emitChartChange(configInstance);
        },

        isInputWidget: function(paramSpec) {
          return isInputWidget(paramSpec);
        },
        isCheckboxWidget: function(paramSpec) {
          return isCheckboxWidget(paramSpec);
        },
        isOptionWidget: function(paramSpec) {
          return isOptionWidget(paramSpec);
        },
        isTextareaWidget: function(paramSpec) {
          return isTextareaWidget(paramSpec);
        },

        parameterChanged: (paramSpec) => {
          configInstance.chartChanged = false;
          configInstance.parameterChanged = true;
          self.emitParameterChange(configInstance);
        },

        parameterOnKeyDown: function(event, paramSpec) {
          const code = event.keyCode || event.which;
          if (code === 13 && isInputWidget(paramSpec)) {
            self.emitParameterChange(configInstance);
          } else if (code === 13 && event.shiftKey && isTextareaWidget(paramSpec)) {
            self.emitParameterChange(configInstance);
          }

          event.stopPropagation(); /** avoid to conflict with paragraph shortcuts */
        },

      },
    };
  }

  transform(tableData) {
    this.columns = tableData.columns; /** used in `getSetting` */
    /** initialize in `transform` instead of `getSetting` because this method is called before */
    serializeSharedAxes(this.config);

    const conf = this.config;
    const chart = getCurrentChart(conf);
    const axis = getCurrentChartAxis(conf);
    const axisSpecs = getCurrentChartAxisSpecs(conf);
    const param = getCurrentChartParam(conf);
    const paramSpecs = getCurrentChartParamSpecs(conf);
    const parsedParam = parseParameter(paramSpecs, param);

    let {transformer, column} = getTransformer(conf, tableData.rows, axisSpecs, axis);

    return {
      chartChanged: conf.chartChanged,
      parameterChanged: conf.parameterChanged,

      chart: chart, /** current chart */
      axis: axis, /** persisted axis */
      parameter: parsedParam, /** persisted parameter */
      column: column,

      transformer: transformer,
    };
  }
}
