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
