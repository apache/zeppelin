import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';

import { get } from 'lodash';

import { GraphConfig, XAxisSetting, XLabelStatus } from '@zeppelin/sdk';
import { Visualization } from '@zeppelin/visualization';

@Component({
  selector: 'zeppelin-visualization-x-axis-setting',
  templateUrl: './x-axis-setting.component.html',
  styleUrls: ['./x-axis-setting.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VisualizationXAxisSettingComponent implements OnInit {
  @Input() visualization: Visualization;
  @Input() mode: 'lineChart' | 'multiBarChart' | 'stackedAreaChart';

  setting: XAxisSetting;
  config: GraphConfig;
  xLabelStatus: XLabelStatus = 'default';
  degree = '-45';
  previousDegree: string;
  constructor(private cdr: ChangeDetectorRef) {}

  onStatusChange() {
    this.setting.xLabelStatus = this.xLabelStatus;
    this.updateConfig();
  }

  onDegreeChange() {
    if (this.degree === this.previousDegree) {
      return;
    }
    const degree = Number.parseInt(this.degree, 10);
    if (Number.isNaN(degree)) {
      this.degree = this.previousDegree;
      return;
    } else {
      this.degree = `${degree}`;
      this.previousDegree = this.degree;
    }
    this.updateConfig();
  }

  updateConfig() {
    this.setting.rotate.degree = this.degree;
    this.setting.xLabelStatus = this.xLabelStatus;
    this.visualization.configChange$.next(this.config);
  }

  init() {
    this.config = this.visualization.getConfig();
    this.setting = this.config.setting[this.mode];
    if (!this.setting.rotate) {
      this.setting.rotate = { degree: '-45' };
    }
    this.xLabelStatus = get(this.setting, ['xLabelStatus'], 'default');
    this.degree = get(this.setting, ['rotate', 'degree'], '-45');
    this.previousDegree = this.degree;
    this.cdr.markForCheck();
  }

  ngOnInit() {
    this.init();
  }
}
