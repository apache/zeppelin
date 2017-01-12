/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import Nvd3ChartVisualization from 'zeppelin-vis/builtins/visualization-nvd3chart';
import PivotTransformation from 'zeppelin-tabledata/pivot';

/**
 * Base class for visualization
 */
export default class horizontalbar extends Nvd3ChartVisualization {
  constructor(targetEl, config) {
    super(targetEl, config)
    this.pivot = new PivotTransformation(config);
  }

  type() {
    return 'multiBarHorizontalChart';
  };

  render(pivot) {
    var d3Data = this.d3DataFromPivot(
      pivot.schema,
      pivot.rows,
      pivot.keys,
      pivot.groups,
      pivot.values,
      true,
      false,
      true);

    super.render(d3Data);
  }

  getTransformation() {
    return this.pivot;
  }
  
  /**
   * Set new config
   */
  setConfig(config) {
    super.setConfig(config);
    this.pivot.setConfig(config);
  };

  configureChart(chart) {
    var self = this;
    chart.yAxis.axisLabelDistance(50);
    chart.yAxis.tickFormat(function(d) {return self.yAxisTickFormat(d);});

    this.chart.stacked(this.config.stacked);

    var self = this;
    this.chart.dispatch.on('stateChange', function(s) {
      self.config.stacked = s.stacked;

      // give some time to animation finish
      setTimeout(function() {
        self.emitConfig(self.config);
      }, 500);
    });
  };
}

