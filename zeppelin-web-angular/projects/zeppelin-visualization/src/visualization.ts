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
import { ComponentRef, ViewContainerRef } from '@angular/core';
import { Subject } from 'rxjs';

import { GraphConfig } from '@zeppelin/sdk';
import { Transformation } from './transformation';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export abstract class Visualization<T = any> {
  // eslint-disable-next-line
  transformed: any;
  componentRef: ComponentRef<T> | null = null;
  configChange$: Subject<GraphConfig> | null = new Subject<GraphConfig>();
  constructor(private config: GraphConfig) {}

  abstract getTransformation(): Transformation;
  abstract render(tableData: unknown): void;
  abstract refresh(): void;
  abstract destroy(): void;

  configChanged() {
    if (!this.configChange$) {
      throw new Error('configChange$ is not initialized');
    }
    return this.configChange$.asObservable();
  }

  setConfig(config: GraphConfig) {
    this.config = config;
    this.refresh();
  }

  getConfig() {
    return this.config;
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type VisualizationConstructor<T = any> = new (
  portalOutlet: CdkPortalOutlet,
  viewContainerRef: ViewContainerRef,
  config: GraphConfig
) => Visualization<T>;
