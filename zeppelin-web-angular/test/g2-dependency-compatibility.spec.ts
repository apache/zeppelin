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

import * as G2 from '@antv/g2';
import { describe, expect, it } from 'vitest';

describe('G2 dependency compatibility', () => {
  it('renders a colored interval chart with the patched color dependency', () => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const chart = new G2.Chart({ container, width: 400, height: 300, renderer: 'svg', animate: false });
    try {
      chart.source([
        { category: 'A', value: 1 },
        { category: 'B', value: 2 }
      ]);
      chart.axis(false);
      chart.legend(false);
      chart.interval().position('category*value').color('category');
      chart.render();
      expect(container.querySelector('svg')).not.toBeNull();
      expect(chart.get('geoms')[0].get('shapeContainer').get('children')).toHaveLength(2);
    } finally {
      chart.destroy();
      container.remove();
    }
  });
});
