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

import { ChangeDetectorRef, ElementRef } from '@angular/core';
import * as G2 from '@antv/g2';
import { GraphConfig } from '@zeppelin/sdk';
import { PivotTransformation, TableData, Visualization } from '@zeppelin/visualization';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { VisualizationScatterSettingComponent } from '../common/scatter-setting/scatter-setting.component';
import { ScatterChartVisualizationComponent } from './scatter-chart-visualization.component';

interface MappedPoint {
  x: number;
  y: number;
  color: string;
  size?: number;
}

interface ScatterFixture {
  chart: G2.Chart;
  component: ScatterChartVisualizationComponent;
  container: HTMLDivElement;
  points: () => MappedPoint[];
  radii: () => number[];
  selectSize: (name?: string) => void;
}

const createChart = (): ScatterFixture => {
  // G2 measures SVG labels on a canvas; jsdom does not implement text measurement.
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    save: vi.fn(),
    restore: vi.fn(),
    measureText: (text: string) => ({ width: text.length * 7 })
  } as unknown as CanvasRenderingContext2D);

  const field = (name: string, index: number) => ({ name, index, aggr: 'sum' });
  const config = new GraphConfig();
  config.mode = 'scatterChart';
  config.keys = [field('x', 0)];
  config.setting.scatterChart = { xAxis: field('x', 0), yAxis: field('y', 1), group: field('group', 4) };

  const table = new TableData();
  table.columns = ['x', 'y', 'size', 'other', 'group'];
  table.rows = [
    { x: '1', y: '10', size: '2', other: '20', group: 'A' },
    { x: '2', y: '10', size: '8', other: '8', group: 'B' },
    { x: '3', y: '10', size: '20', other: '2', group: 'A' }
  ];

  const visualization = {
    getConfig: () => config,
    transformed: new PivotTransformation(config).transform(table)
  } as Visualization;

  const component = new ScatterChartVisualizationComponent(visualization, {
    markForCheck: vi.fn()
  } as unknown as ChangeDetectorRef);
  component.scatterSettingComponent = { init: vi.fn() } as unknown as VisualizationScatterSettingComponent;

  const container = document.createElement('div');
  document.body.appendChild(container);
  component.container = new ElementRef(container);

  const chart = new G2.Chart({ container, width: 600, height: 400, renderer: 'svg', animate: false });
  component.chart = chart;
  const points = (): MappedPoint[] =>
    chart
      .get('geoms')[0]
      .get('dataArray')
      .flat()
      .sort((a: MappedPoint, b: MappedPoint) => a.x - b.x);
  const radii = (): number[] =>
    chart
      .get('geoms')[0]
      .get('shapeContainer')
      .get('children')
      .map((shape: { attr: (name: string) => number }) => shape.attr('radius'))
      .sort((a: number, b: number) => a - b);

  const selectSize = (name?: string) => {
    config.setting.scatterChart!.size = name ? field(name, table.columns.indexOf(name)) : undefined;
    component.render();
  };
  return { chart, component, container, points, radii, selectSize };
};

describe('ScatterChartVisualizationComponent point sizes', () => {
  let fixture: ScatterFixture;

  afterEach(() => {
    fixture?.component.ngOnDestroy();
    fixture?.container.remove();
    vi.restoreAllMocks();
  });

  it('uses the selected size values independently of Y while preserving positions and groups', () => {
    fixture = createChart();
    fixture.selectSize();
    const positionsAndGroups = () => fixture.points().map(({ x, y, color }) => ({ x, y, color }));
    const baseline = positionsAndGroups();

    fixture.selectSize('size');
    const sizes = fixture.points().map(point => point.size!);
    expect(sizes[0]).toBeLessThan(sizes[1]);
    expect(sizes[1]).toBeLessThan(sizes[2]);
    expect(positionsAndGroups()).toEqual(baseline);

    fixture.selectSize('other');
    expect(fixture.points().map(point => point.size)).toEqual([...sizes].reverse());
    expect(positionsAndGroups()).toEqual(baseline);
  });

  it('restores the default rendered radii when the size selection is cleared', () => {
    fixture = createChart();
    fixture.selectSize();
    const defaultRadii = fixture.radii();

    fixture.selectSize('size');
    expect(fixture.radii()).not.toEqual(defaultRadii);

    fixture.selectSize();
    expect(fixture.radii()).toEqual(defaultRadii);
  });

  it('keeps Y values in tooltips and preserves the group legend across size selections', () => {
    fixture = createChart();
    fixture.selectSize();
    const tooltipContent = () =>
      fixture
        .points()
        .map(point =>
          fixture.chart
            .getTooltipItems(point)
            .map((item: { name: string; value: string }) => ({ name: item.name, value: item.value }))
        );
    const legendItems = () => {
      const legends: Record<string, Array<{ get: (name: string) => unknown }>> = fixture.chart.get(
        'legendController'
      ).legends;
      return Object.values(legends)
        .flat()
        .map(legend => legend.get('items'));
    };
    const tooltips = tooltipContent();
    const legends = legendItems();
    expect(tooltips.flat()).toContainEqual({ name: 'y.A', value: '10' });
    expect(legends).toHaveLength(1);

    for (const size of ['size', 'other', undefined]) {
      fixture.selectSize(size);
      expect(tooltipContent()).toEqual(tooltips);
      expect(legendItems()).toEqual(legends);
    }
  });
});
