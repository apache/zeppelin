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
