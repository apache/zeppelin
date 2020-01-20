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

const DEFAULT_COLOR = '#03578c';
const COLOR_PLATE_8 = ['#03578c', '#179bd4', '#bf4f07', '#005041', '#8543E0', '#57c2e9', '#03138c', '#8c0357'];

const COLOR_PLATE_16 = [
  '#03588C',
  '#82B8D9',
  '#025959',
  '#FACC14',
  '#E6965C',
  '#223273',
  '#7564CC',
  '#8543E0',
  '#5C8EE6',
  '#13C2C2',
  '#5CA3E6',
  '#3436C7',
  '#B381E6',
  '#F04864',
  '#D598D9'
];
const COLOR_PLATE_24 = [
  '#03588C',
  '#66B5FF',
  '#82B8D9',
  '#025959',
  '#027368',
  '#9AE65C',
  '#FACC14',
  '#E6965C',
  '#57AD71',
  '#223273',
  '#738AE6',
  '#7564CC',
  '#8543E0',
  '#A877ED',
  '#5C8EE6',
  '#13C2C2',
  '#70E0E0',
  '#5CA3E6',
  '#3436C7',
  '#8082FF',
  '#DD81E6',
  '#F04864',
  '#FA7D92',
  '#D598D9'
];
const COLOR_PIE = ['#03588C', '#13C2C2', '#025959', '#FACC14', '#F04864', '#8543E0', '#3436C7', '#223273'];
const COLOR_PIE_16 = [
  '#03588C',
  '#73C9E6',
  '#13C2C2',
  '#6CD9B3',
  '#025959',
  '#9DD96C',
  '#FACC14',
  '#E6965C',
  '#F04864',
  '#D66BCA',
  '#8543E0',
  '#8E77ED',
  '#3436C7',
  '#737EE6',
  '#223273',
  '#7EA2E6'
];

const zeppelinTheme = {
  defaultColor: DEFAULT_COLOR,
  colors: COLOR_PLATE_8,
  colors_16: COLOR_PLATE_16,
  colors_24: COLOR_PLATE_24,
  colors_pie: COLOR_PIE,
  colors_pie_16: COLOR_PIE_16,
  shape: {
    point: {
      fill: DEFAULT_COLOR
    },
    hollowPoint: {
      stroke: DEFAULT_COLOR
    },
    interval: {
      fill: DEFAULT_COLOR
    },
    hollowInterval: {
      stroke: DEFAULT_COLOR
    },
    area: {
      fill: DEFAULT_COLOR
    },
    polygon: {
      fill: DEFAULT_COLOR
    },
    hollowPolygon: {
      stroke: DEFAULT_COLOR
    },
    hollowArea: {
      stroke: DEFAULT_COLOR
    },
    line: {
      stroke: DEFAULT_COLOR
    },
    edge: {
      stroke: DEFAULT_COLOR
    },
    schema: {
      stroke: DEFAULT_COLOR
    }
  }
};

export function setTheme() {
  const theme = G2.Util.deepMix(G2.Global, zeppelinTheme);
  // tslint:disable-next-line:no-any
  (G2.Global as any).setTheme(theme);
}
