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

import type { TooltipItem } from '@antv/g2';

// G2 3.5.x ships declarations validated against 3.0.4. These runtime APIs
// are used by the shell tests but are absent from those declarations.
declare module '@antv/g2' {
  interface ChartProps {
    renderer: 'canvas' | 'svg';
  }

  interface Chart {
    get(name: 'geoms'): Geom[];
    get(name: 'legendController'): {
      legends: Record<string, Array<{ get(name: 'items'): unknown }>>;
    };
    getTooltipItems(point: { x: number; y: number }): Array<Pick<TooltipItem, 'name' | 'value'>>;
  }

  interface Geom {
    get(name: 'dataArray'): Array<Array<Record<string, unknown>>> | undefined;
    get(name: 'shapeContainer'): {
      get(name: 'children'): Array<{ attr(name: 'radius'): unknown }>;
    } | null;
  }
}
