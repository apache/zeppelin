import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  Inject,
  OnInit,
  ViewChild
} from '@angular/core';

import { G2VisualizationComponentBase, Visualization, VISUALIZATION } from '@zeppelin/visualization';

import { VisualizationPivotSettingComponent } from '../common/pivot-setting/pivot-setting.component';
import { calcTickCount } from '../common/util/calc-tick-count';
import { setChartXAxis } from '../common/util/set-x-axis';
import { VisualizationXAxisSettingComponent } from '../common/x-axis-setting/x-axis-setting.component';

@Component({
  selector: 'zeppelin-area-chart-visualization',
  templateUrl: './area-chart-visualization.component.html',
  styleUrls: ['./area-chart-visualization.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AreaChartVisualizationComponent extends G2VisualizationComponentBase implements OnInit, AfterViewInit {
  @ViewChild('container', { static: false }) container: ElementRef<HTMLDivElement>;
  @ViewChild(VisualizationXAxisSettingComponent, { static: false })
  xAxisSettingComponent: VisualizationXAxisSettingComponent;
  @ViewChild(VisualizationPivotSettingComponent, { static: false })
  pivotSettingComponent: VisualizationPivotSettingComponent;
  style: 'stream' | 'expand' | 'stack' = 'stack';

  constructor(@Inject(VISUALIZATION) public visualization: Visualization, private cdr: ChangeDetectorRef) {
    super(visualization);
  }

  viewChange() {
    this.config.setting.stackedAreaChart.style = this.style;
    this.visualization.configChange$.next(this.config);
  }

  ngOnInit() {}

  refreshSetting() {
    this.style = this.config.setting.stackedAreaChart.style;
    this.pivotSettingComponent.init();
    this.xAxisSettingComponent.init();
    this.cdr.markForCheck();
  }

  ngAfterViewInit(): void {
    this.render();
  }

  setScale() {
    const key = this.getKey();
    const tickCount = calcTickCount(this.container.nativeElement);
    this.chart.scale(key, {
      tickCount,
      type: 'cat'
    });
  }

  renderBefore() {
    const key = this.getKey();
    this.setScale();
    if (this.style === 'stack') {
      // area:stack
      this.chart
        .areaStack()
        .position(`${key}*__value__`)
        .color('__key__');
    } else if (this.style === 'stream') {
      // area:stream
      this.chart
        .area()
        .position(`${key}*__value__`)
        .adjust(['stack', 'symmetric'])
        .color('__key__');
    } else {
      // area:percent
      this.chart
        .areaStack()
        .position(`${key}*__percent__`)
        .color('__key__');
    }

    setChartXAxis(this.visualization, 'stackedAreaChart', this.chart, key);
  }
}
