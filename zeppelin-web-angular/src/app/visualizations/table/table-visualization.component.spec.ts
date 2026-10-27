/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ChangeDetectorRef } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';

import { Visualization } from '@zeppelin/visualization';

import { TableVisualizationComponent } from './table-visualization.component';

const createComponent = () => {
  const rows = Array.from({ length: 25 }, (_, index) => ({ id: String(index + 1) }));
  const visualization = { transformed: { columns: ['id'], rows } };
  const cdr = { markForCheck: vi.fn() } as unknown as ChangeDetectorRef;
  const component = new TableVisualizationComponent(visualization as unknown as Visualization, cdr);
  component.render();
  return { component, visualization };
};

describe('TableVisualizationComponent page range', () => {
  it('moves to the last available page when a rerun shrinks the result', () => {
    const { component, visualization } = createComponent();
    component.pageIndex = 3;
    visualization.transformed.rows = visualization.transformed.rows.slice(0, 15);

    component.render();

    expect(component.pageIndex).toBe(2);
    expect(component.rows).toEqual(visualization.transformed.rows);
  });

  it('resets an empty rerun to page one and keeps it there when data returns', () => {
    const { component, visualization } = createComponent();
    const rows = visualization.transformed.rows;
    component.pageIndex = 3;
    visualization.transformed.rows = [];

    component.render();

    expect(component.pageIndex).toBe(1);
    expect(component.rows).toEqual([]);

    visualization.transformed.rows = rows;
    component.render();

    expect(component.pageIndex).toBe(1);
    expect(component.rows).toEqual(rows);
  });

  it('resets a zero-match filter and restores the first page after the filter is cleared', () => {
    const { component, visualization } = createComponent();
    component.pageIndex = 2;
    component.onTermChange('id', 'no-such-value');

    component.onSearch();

    expect(component.rows).toEqual([]);
    expect(component.pageIndex).toBe(1);

    component.onTermChange('id', '');
    component.onSearch();

    expect(component.rows).toEqual(visualization.transformed.rows);
    expect(component.pageIndex).toBe(1);
  });

  it('retains a valid page at an exact page-size boundary after a rerun', () => {
    const { component, visualization } = createComponent();
    component.pageIndex = 2;
    visualization.transformed.rows = visualization.transformed.rows.slice(0, 20);

    component.render();

    expect(component.pageIndex).toBe(2);
    expect(component.rows).toEqual(visualization.transformed.rows);
  });
});
