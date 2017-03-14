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

import Nvd3ChartVisualization from './visualization-nvd3chart';
import PivotTransformation from '../../tabledata/pivot';

/**
 * Visualize data in bar char
 */
export default class BarchartVisualization extends Nvd3ChartVisualization {
  constructor(targetEl, config) {
    super(targetEl, config);

    this.pivot = new PivotTransformation(config);
  };

  type() {
    return 'multiBarChart';
  };

  getTransformation() {
    return this.pivot;
  };

  render(pivot) {
    var d3Data = this.d3DataFromPivot(
      pivot.schema,
      pivot.rows,
      pivot.keys,
      pivot.groups,
      pivot.values,
      true,
      true,
      true);

    super.render(d3Data);
    this.config.changeXLabel(this.config.xLabelStatus);
  };

  /**
   * Set new config
   */
  setConfig(config) {
    super.setConfig(config);
    this.pivot.setConfig(config);
  };

  configureChart(chart) {
    var self = this;
    var configObj = self.config;

    chart.yAxis.axisLabelDistance(50);
    chart.yAxis.tickFormat(function(d) {return self.yAxisTickFormat(d);});

    self.chart.stacked(this.config.stacked);

    self.config.changeXLabel = function(type) {
      switch (type) {
        case 'default':
          self.chart._options['showXAxis'] = true;
          self.chart._options['margin'] = {bottom: 50};
          self.chart.xAxis.rotateLabels(0);
          configObj.xLabelStatus = 'default';
          break;
        case 'rotate':
          self.chart._options['showXAxis'] = true;
          self.chart._options['margin'] = {bottom: 140};
          self.chart.xAxis.rotateLabels(-45);
          configObj.xLabelStatus = 'rotate';
          break;
        case 'hide':
          self.chart._options['showXAxis'] = false;
          self.chart._options['margin'] = {bottom: 50};
          d3.select('#' + self.targetEl[0].id + '> svg').select('g.nv-axis.nv-x').selectAll('*').remove();
          configObj.xLabelStatus = 'hide';
          break;
      }
    };

    self.config.isXLabelStatus = function(type) {
      if (configObj.xLabelStatus === type) {
        return true;
      } else {
        return false;
      }
    };

    this.chart.dispatch.on('stateChange', function(s) {
      configObj.stacked = s.stacked;

      // give some time to animation finish
      setTimeout(function() {
        self.emitConfig(configObj);
      }, 500);
    });
  };



  getSetting(chart) {
    var self = this;
    var configObj = self.config;

    // default to visualize xLabel
    if (typeof(configObj.xLabelStatus) === 'undefined') {
      configObj.changeXLabel('default');
    }

    return {
      template: `<div>
          xAxis :
      </div>

      <div class='btn-group'>
        <button type="button"
              class="xLabel btn btn-default btn-sm"
              ng-class="{'active' : this.config.isXLabelStatus('default')}"
              ng-click="save('default')" >
            Default
        </button>

        <button type="button"
              class="btn btn-default btn-sm"
              ng-class="{'active' : this.config.isXLabelStatus('rotate')}"
              ng-click="save('rotate')" >
            Rotate
        </button>

        <button type="button"
              class="btn btn-default btn-sm"
              ng-class="{'active' : this.config.isXLabelStatus('hide')}"
              ng-click="save('hide')" >
            Hide
        </button>
      </div>`,
      scope: {
        config: configObj,
        save: function(type) {
          configObj.changeXLabel(type);
          self.emitConfig(configObj);
        }
      }
    };
  };
}
