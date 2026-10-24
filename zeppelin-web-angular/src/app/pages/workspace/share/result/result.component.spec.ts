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

import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ChangeDetectorRef, Injector, ViewContainerRef } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { DatasetType, GraphConfig } from '@zeppelin/sdk';
import { TableTransformation, Visualization } from '@zeppelin/visualization';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@zeppelin/services', () => ({
  NgZService: class {},
  RuntimeCompilerService: class {}
}));
vi.mock('@zeppelin/visualizations', () => ({
  AreaChartVisualization: class {},
  BarChartVisualization: class {},
  LineChartVisualization: class {},
  PieChartVisualization: class {},
  ScatterChartVisualization: class {},
  TableVisualization: class {}
}));

import { DynamicTemplate, NgZService, RuntimeCompilerService } from '@zeppelin/services';
import { NotebookParagraphResultComponent } from './result.component';

const component = (compiler: Partial<RuntimeCompilerService> = {}) =>
  new NotebookParagraphResultComponent(
    {} as Injector,
    {} as ViewContainerRef,
    { detectChanges: vi.fn(), markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    compiler as RuntimeCompilerService,
    {} as DomSanitizer,
    {} as NgZService
  );

const pendingResult = <T>() => {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const pendingCompilation = () => pendingResult<DynamicTemplate>();

const flushCompilation = async () => {
  await Promise.resolve();
  await Promise.resolve();
};

describe('finite dataset rendering', () => {
  it('encodes SVG output as an image URL', () => {
    const result = component();
    const svg = '<svg xmlns="http://www.w3.org/2000/svg"><text>한글 # &</text></svg>';
    result.result = { type: DatasetType.SVG, data: svg };
    result.angularComponent = {} as DynamicTemplate;
    result.renderDefaultDisplay();
    expect(result.imgData).toBe(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`);
    expect(result.angularComponent).toBeNull();
  });

  it('clears a previous Angular display for NULL output', () => {
    const result = component();
    result.result = { type: DatasetType.NULL, data: '' };
    result.angularComponent = {} as DynamicTemplate;
    result.renderDefaultDisplay();
    expect(result.angularComponent).toBeNull();
  });

  it.each([DatasetType.NULL, DatasetType.NETWORK])('leaves %s without a display renderer', type => {
    const result = component();
    result.result = { type, data: '' };
    const renderHTML = vi.spyOn(result, 'renderHTML');
    const renderAngular = vi.spyOn(result, 'renderAngular');
    result.renderDefaultDisplay();
    expect(renderHTML).not.toHaveBeenCalled();
    expect(renderAngular).not.toHaveBeenCalled();
    expect(result.frontEndError).toBe('');
  });
});

describe('SVG and NULL invalidate pending Angular output', () => {
  it.each([DatasetType.SVG, DatasetType.NULL])('ignores stale compilation success after %s output', async type => {
    const pending = pendingCompilation();
    const result = component({ createAndCompileTemplate: vi.fn().mockReturnValue(pending.promise) });
    result.result = { type: DatasetType.ANGULAR, data: '<p>previous</p>' };
    result.renderDefaultDisplay();
    result.result = { type, data: '<svg xmlns="http://www.w3.org/2000/svg" />' };
    result.renderDefaultDisplay();

    pending.resolve({} as DynamicTemplate);
    await flushCompilation();

    expect(result.angularComponent).toBeNull();
    expect(result.frontEndError).toBe('');
  });

  it.each([DatasetType.SVG, DatasetType.NULL])('ignores stale compilation errors after %s output', async type => {
    const pending = pendingCompilation();
    const result = component({ createAndCompileTemplate: vi.fn().mockReturnValue(pending.promise) });
    result.result = { type: DatasetType.ANGULAR, data: '<p>previous</p>' };
    result.renderDefaultDisplay();
    result.result = { type, data: '<svg xmlns="http://www.w3.org/2000/svg" />' };
    result.renderDefaultDisplay();

    pending.reject(new Error('obsolete compilation'));
    await flushCompilation();

    expect(result.angularComponent).toBeNull();
    expect(result.frontEndError).toBe('');
  });
});

describe('NotebookParagraphResultComponent HTML highlighting', () => {
  it('preserves HTML and highlights code with the patched highlight.js release', () => {
    const sanitizer = { bypassSecurityTrustHtml: (html: string) => html } as unknown as DomSanitizer;
    const component = new NotebookParagraphResultComponent(
      {} as Injector,
      {} as ViewContainerRef,
      {} as ChangeDetectorRef,
      {} as RuntimeCompilerService,
      sanitizer,
      {} as NgZService
    );
    component.result = {
      type: DatasetType.HTML,
      data: '<p>Result</p><pre><code class="language-javascript">const value = 42;</code></pre>'
    };

    component.renderHTML();

    const output = document.createElement('div');
    output.innerHTML = component.innerHTML as string;
    expect(output.querySelector('p')?.textContent).toBe('Result');
    expect(output.querySelector('code')?.textContent).toBe('const value = 42;');
    expect(output.querySelector('.hljs-keyword')?.textContent).toBe('const');
  });
});

describe('built-in visualizations without external bundles', () => {
  class TestVisualization extends Visualization {
    render = vi.fn();
    refresh = vi.fn();
    destroy = vi.fn();

    constructor(_outlet: CdkPortalOutlet, _container: ViewContainerRef, config: GraphConfig) {
      super(config);
    }

    getTransformation() {
      return new TableTransformation(this.getConfig());
    }
  }

  const tableResult = (mode: string) => {
    const result = component();
    result.result = { type: DatasetType.TABLE, data: 'column\nvalue' };
    result.config = { graph: { ...new GraphConfig(), mode } };
    result.visualizations.forEach(item => (item.Class = TestVisualization));
    return result;
  };

  it.each(['table', 'multiBarChart', 'pieChart', 'lineChart', 'stackedAreaChart', 'scatterChart'])(
    'renders saved %s settings with the built-in visualization',
    mode => {
      const result = tableResult(mode);
      result.config!.graph.height = 420;
      result.renderDefaultDisplay();
      const instance = result.getCurrentVisualization().instance!;

      expect(instance.getConfig()).toBe(result.config!.graph);
      expect(instance.getConfig().height).toBe(420);
      expect(result.tableData.columns).toEqual(['column']);
      expect(result.tableData.rows).toEqual([{ column: 'value' }]);
      expect(result.frontEndError).toBe('');
    }
  );

  it('falls back to table data while preserving saved external visualization settings', () => {
    const result = tableResult('external-chart');
    const original = JSON.parse(JSON.stringify(result.config));
    const configChange = vi.spyOn(result.configChange, 'emit');
    result.renderDefaultDisplay();

    expect(result.getCurrentVisualization().id).toBe('table');
    expect(result.getCurrentVisualization().instance!.getConfig().mode).toBe('table');
    expect(result.tableData.rows).toEqual([{ column: 'value' }]);
    expect(result.config).toEqual(original);
    expect(configChange).not.toHaveBeenCalled();
    expect(result.frontEndError).toContain('external-chart');

    result.switchMode('table');
    expect(result.config!.graph.mode).toBe('table');
    expect(result.frontEndError).toBe('');
  });

  it('persists user chart changes and destroys the previous visualization', () => {
    const result = tableResult('table');
    result.renderDefaultDisplay();
    const previous = result.getCurrentVisualization().instance as TestVisualization;
    const configChange = vi.spyOn(result.configChange, 'emit');
    result.switchMode('lineChart');
    const graph = { ...result.config!.graph, height: 420 };
    result.getCurrentVisualization().instance!.configChange$!.next(graph);

    expect(previous.destroy).toHaveBeenCalledOnce();
    expect(result.config!.graph).toBe(graph);
    expect(configChange).toHaveBeenLastCalledWith({ graph });
    expect(result.frontEndError).toBe('');
  });
});
