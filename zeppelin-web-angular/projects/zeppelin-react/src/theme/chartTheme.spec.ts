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

import { describe, expect, it } from 'vitest';
import { applyChartTheme, CHART_THEME } from './chartTheme';

// chart.js ships '#666' text and 'rgba(0, 0, 0, 0.1)' grid lines, both of
// which are meant for a light canvas.
const chartJsDefaults = () => ({ defaults: { color: '#666', borderColor: 'rgba(0, 0, 0, 0.1)' } });

describe('applyChartTheme', () => {
  it('replaces the chart.js defaults with the dark palette', () => {
    const chart = chartJsDefaults();

    applyChartTheme(chart, 'dark');

    expect(chart.defaults.color).toBe(CHART_THEME.dark.text);
    expect(chart.defaults.borderColor).toBe(CHART_THEME.dark.grid);
  });

  it('replaces the chart.js defaults with the light palette', () => {
    const chart = chartJsDefaults();

    applyChartTheme(chart, 'light');

    expect(chart.defaults.color).toBe(CHART_THEME.light.text);
    expect(chart.defaults.borderColor).toBe(CHART_THEME.light.grid);
  });

  it('leaves no chart.js default in place for either mode', () => {
    // The point of the issue: axis labels at '#666' sit at about 3.2:1 against
    // the shell's dark background, below the 4.5:1 the rest of the UI meets.
    const untouched = chartJsDefaults().defaults;

    for (const mode of ['light', 'dark'] as const) {
      const chart = chartJsDefaults();
      applyChartTheme(chart, mode);
      expect(chart.defaults.color).not.toBe(untouched.color);
      expect(chart.defaults.borderColor).not.toBe(untouched.borderColor);
    }
  });
});
