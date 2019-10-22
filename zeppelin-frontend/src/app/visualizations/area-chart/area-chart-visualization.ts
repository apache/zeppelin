import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ViewContainerRef } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import { G2VisualizationBase, VisualizationComponentPortal } from '@zeppelin/visualization';

import { AreaChartVisualizationComponent } from './area-chart-visualization.component';

export class AreaChartVisualization extends G2VisualizationBase {
  componentPortal = new VisualizationComponentPortal<AreaChartVisualization, AreaChartVisualizationComponent>(
    this,
    AreaChartVisualizationComponent,
    this.portalOutlet,
    this.viewContainerRef
  );

  constructor(config: GraphConfig, private portalOutlet: CdkPortalOutlet, private viewContainerRef: ViewContainerRef) {
    super(config);
  }
}
