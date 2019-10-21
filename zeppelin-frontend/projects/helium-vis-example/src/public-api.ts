/*
 * Public API Surface of helium-vis-example
 */

import { createHeliumPackage, HeliumPackageType } from '@zeppelin/helium';
import { JsonVisComponent } from './json-vis.component';
import { JsonVisModule } from './json-vis.module';
import { JsonVisualization } from './json-visualization';

export default createHeliumPackage({
  name: 'helium-vis-example',
  id: 'heliumVisExample',
  icon: 'appstore',
  type: HeliumPackageType.Visualization,
  module: JsonVisModule,
  component: JsonVisComponent,
  visualization: JsonVisualization
});
