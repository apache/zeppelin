import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ViewContainerRef } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import {
  TableTransformation,
  Transformation,
  Visualization,
  VisualizationComponentPortal
} from '@zeppelin/visualization';

import { TableVisualizationComponent } from './table-visualization.component';

export class TableVisualization extends Visualization<TableVisualizationComponent> {
  tableTransformation = new TableTransformation(this.getConfig());
  componentPortal = new VisualizationComponentPortal<TableVisualization, TableVisualizationComponent>(
    this,
    TableVisualizationComponent,
    this.portalOutlet,
    this.viewContainerRef
  );
  constructor(config: GraphConfig, private portalOutlet: CdkPortalOutlet, private viewContainerRef: ViewContainerRef) {
    super(config);
  }

  destroy(): void {
    if (this.componentRef) {
      this.componentRef.destroy();
      this.componentRef = null;
    }
    this.configChange$.complete();
    this.configChange$ = null;
  }

  getTransformation(): Transformation {
    return this.tableTransformation;
  }

  refresh(): void {}

  render(data): void {
    this.transformed = data;
    if (!this.componentRef) {
      this.componentRef = this.componentPortal.attachComponentPortal();
    }
    this.componentRef.instance.render();
  }
}
