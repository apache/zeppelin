import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ViewContainerRef } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import { G2VisualizationBase, VisualizationComponentPortal } from '@zeppelin/visualization';

import { ScatterChartVisualizationComponent } from './scatter-chart-visualization.component';

export class ScatterChartVisualization extends G2VisualizationBase {
  componentPortal = new VisualizationComponentPortal<ScatterChartVisualization, ScatterChartVisualizationComponent>(
    this,
    ScatterChartVisualizationComponent,
    this.portalOutlet,
    this.viewContainerRef
  );

  constructor(config: GraphConfig, private portalOutlet: CdkPortalOutlet, private viewContainerRef: ViewContainerRef) {
    super(config);
  }
}
