import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ViewContainerRef } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import { G2VisualizationBase, VisualizationComponentPortal } from '@zeppelin/visualization';

import { PieChartVisualizationComponent } from './pie-chart-visualization.component';

export class PieChartVisualization extends G2VisualizationBase {
  componentPortal = new VisualizationComponentPortal<PieChartVisualization, PieChartVisualizationComponent>(
    this,
    PieChartVisualizationComponent,
    this.portalOutlet,
    this.viewContainerRef
  );

  constructor(config: GraphConfig, private portalOutlet: CdkPortalOutlet, private viewContainerRef: ViewContainerRef) {
    super(config);
  }
}
