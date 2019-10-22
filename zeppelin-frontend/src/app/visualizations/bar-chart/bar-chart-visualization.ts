import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ViewContainerRef } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import { G2VisualizationBase, VisualizationComponentPortal } from '@zeppelin/visualization';

import { BarChartVisualizationComponent } from './bar-chart-visualization.component';

export class BarChartVisualization extends G2VisualizationBase {
  componentPortal = new VisualizationComponentPortal<BarChartVisualization, BarChartVisualizationComponent>(
    this,
    BarChartVisualizationComponent,
    this.portalOutlet,
    this.viewContainerRef
  );
  constructor(config: GraphConfig, private portalOutlet: CdkPortalOutlet, private viewContainerRef: ViewContainerRef) {
    super(config);
  }
}
