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

import { HostThemeMode } from './hostTheme';

/**
 * Charts are painted on a canvas, so no stylesheet reaches them. chart.js
 * defaults to '#666' text and 'rgba(0, 0, 0, 0.1)' grid lines, which leaves
 * axis labels at roughly 3.2:1 against the dark background and the grid
 * invisible. These values follow antd's secondary text and split tokens.
 */
export const CHART_THEME: Record<HostThemeMode, { text: string; grid: string }> = {
  light: { text: 'rgba(0, 0, 0, 0.65)', grid: 'rgba(0, 0, 0, 0.06)' },
  dark: { text: 'rgba(255, 255, 255, 0.65)', grid: 'rgba(255, 255, 255, 0.12)' }
};

/** The two globals chart.js resolves ticks, legend labels and grid lines from. */
export interface ChartThemeTarget {
  defaults: {
    color: unknown;
    borderColor: unknown;
  };
}

export const applyChartTheme = (chart: ChartThemeTarget, mode: HostThemeMode): void => {
  chart.defaults.color = CHART_THEME[mode].text;
  chart.defaults.borderColor = CHART_THEME[mode].grid;
};
