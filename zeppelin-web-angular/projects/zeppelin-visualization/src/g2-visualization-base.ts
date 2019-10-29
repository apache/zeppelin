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

import { GraphConfig } from '@zeppelin/sdk';

import { G2VisualizationComponentBase } from './g2-visualization-component-base';
import { PivotTransformation } from './pivot-transformation';
import { Transformation } from './transformation';
import { Visualization } from './visualization';
import { VisualizationComponentPortal } from './visualization-component-portal';

export abstract class G2VisualizationBase extends Visualization<G2VisualizationComponentBase> {
  pivot = new PivotTransformation(this.getConfig());
  abstract componentPortal: VisualizationComponentPortal<G2VisualizationBase, G2VisualizationComponentBase>;

  constructor(config: GraphConfig) {
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
    return this.pivot;
  }

  refresh(): void {
    if (this.componentRef) {
      this.componentRef.instance.refresh();
    }
  }

  render(data): void {
    this.transformed = data;
    if (this.componentRef) {
      this.componentRef.instance.refreshSetting();
      this.componentRef.instance.render();
    } else {
      this.componentRef = this.componentPortal.attachComponentPortal();
    }
  }
}
