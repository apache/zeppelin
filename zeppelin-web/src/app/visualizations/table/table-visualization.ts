/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
