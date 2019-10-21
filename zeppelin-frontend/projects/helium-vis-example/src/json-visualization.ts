import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ComponentFactoryResolver, ViewContainerRef } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import {
  TableTransformation,
  Transformation,
  Visualization,
  VisualizationComponentPortal
} from '@zeppelin/visualization';

import { JsonVisComponent } from './json-vis.component';

export class JsonVisualization extends Visualization<JsonVisComponent> {
  tableTransformation = new TableTransformation(this.getConfig());
  componentPortal = new VisualizationComponentPortal<JsonVisualization, JsonVisComponent>(
    this,
    JsonVisComponent,
    this.portalOutlet,
    this.viewContainerRef,
    this.componentFactoryResolver
  );
  constructor(config: GraphConfig,
              private portalOutlet: CdkPortalOutlet,
              private viewContainerRef: ViewContainerRef,
              private componentFactoryResolver?: ComponentFactoryResolver) {
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
