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

import { CdkPortalOutlet, PortalModule } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Injector,
  NO_ERRORS_SCHEMA,
  provideZoneChangeDetection,
  ViewContainerRef
} from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { DatasetType, GraphConfig } from '@zeppelin/sdk';
import { HeliumClassicVisualization, HeliumClassicVisualizationConstructor } from '@zeppelin/interfaces';
import { Visualization } from '@zeppelin/visualization';
import { EMPTY, Subscription } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@zeppelin/services', () => ({
  ClassicVisualizationService: class {},
  HeliumService: class {},
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

import {
  ClassicVisualizationService,
  DynamicTemplate,
  HeliumService,
  NgZService,
  RuntimeCompilerService
} from '@zeppelin/services';
import { NotebookParagraphResultComponent } from './result.component';
import template from './result.component.html?raw';

const component = (
  compiler: Partial<RuntimeCompilerService> = {},
  classic: Partial<ClassicVisualizationService> = {}
) =>
  new NotebookParagraphResultComponent(
    {} as Injector,
    {} as ViewContainerRef,
    { detectChanges: vi.fn(), markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    compiler as RuntimeCompilerService,
    {} as DomSanitizer,
    {} as NgZService,
    {} as HeliumService,
    { destroyAllInstances: vi.fn(), ...classic } as unknown as ClassicVisualizationService
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

  it('reports errors from the current Angular compilation', async () => {
    const pending = pendingCompilation();
    const result = component({ createAndCompileTemplate: vi.fn().mockReturnValue(pending.promise) });
    result.result = { type: DatasetType.ANGULAR, data: '<p>current</p>' };
    result.renderDefaultDisplay();

    pending.reject(new Error('current compilation failed'));
    await flushCompilation();

    expect(result.angularComponent).toBeNull();
    expect(result.frontEndError).toBe('current compilation failed');
  });
});

describe('result type transitions', () => {
  it.each([DatasetType.SVG, DatasetType.NULL])('destroys an attached modern visualization for %s', type => {
    const result = component();
    const destroy = vi.fn();
    const detach = vi.fn();
    const unsubscribe = vi.fn();
    const subscription = new Subscription(unsubscribe);
    result.visualizations[0].instance = { destroy } as unknown as Visualization;
    result.visualizations[0].changeSubscription = subscription;
    result.portalOutlet = { hasAttached: () => true, detach } as unknown as CdkPortalOutlet;
    result.result = { type, data: '<svg />' };

    result.renderDefaultDisplay();

    expect(destroy).toHaveBeenCalledOnce();
    expect(detach).toHaveBeenCalledOnce();
    expect(unsubscribe).toHaveBeenCalledOnce();
    expect(result.visualizations[0].instance).toBeUndefined();
    expect(result.visualizations[0].changeSubscription).toBeNull();
  });

  it.each([DatasetType.SVG, DatasetType.NULL])('destroys a previous classic visualization for %s', type => {
    const destroyInstance = vi.fn();
    const result = component({}, { destroyInstance });
    result.id = 'paragraph';
    result.visualizations.push({
      id: 'classic',
      name: 'classic',
      isClassic: true,
      icon: {},
      Class: vi.fn() as unknown as HeliumClassicVisualizationConstructor,
      instance: {} as HeliumClassicVisualization,
      changeSubscription: null
    });
    result.result = { type, data: '<svg />' };

    result.renderDefaultDisplay();

    expect(destroyInstance).toHaveBeenCalledExactlyOnceWith('pparagraph_classic');
    expect(result.visualizations.at(-1)?.instance).toBeUndefined();
  });

  it('preserves an existing visualization during a TABLE refresh', () => {
    const result = component();
    const destroy = vi.fn();
    const instance = { destroy } as unknown as Visualization;
    result.visualizations[0].instance = instance;
    result.result = { type: DatasetType.TABLE, data: 'column\nvalue' };
    const renderGraph = vi.spyOn(result, 'renderGraph').mockImplementation(() => {});

    result.renderDefaultDisplay();

    expect(renderGraph).toHaveBeenCalledOnce();
    expect(destroy).not.toHaveBeenCalled();
    expect(result.visualizations[0].instance).toBe(instance);
  });

  it.each([DatasetType.SVG, DatasetType.NULL])('cancels a pending classic visualization after %s', async type => {
    const pending = pendingResult<HeliumClassicVisualization | undefined>();
    const createClassicVisualization = vi.fn().mockReturnValue(pending.promise);
    const result = component({}, { createClassicVisualization });
    result.id = 'paragraph';
    result.visualizations.push({
      id: 'classic',
      name: 'classic',
      isClassic: true,
      icon: {},
      Class: vi.fn() as unknown as HeliumClassicVisualizationConstructor,
      instance: undefined,
      changeSubscription: null
    });
    result.config = { graph: { ...new GraphConfig(), mode: 'classic' } };
    result.result = { type: DatasetType.TABLE, data: 'column\nvalue' };
    result.renderDefaultDisplay();
    const isCurrentRender = createClassicVisualization.mock.calls[0][5] as () => boolean;
    expect(isCurrentRender()).toBe(true);

    result.result = { type, data: '<svg />' };
    result.renderDefaultDisplay();
    expect(isCurrentRender()).toBe(false);
    pending.resolve(undefined);
    await flushCompilation();

    expect(result.visualizations.at(-1)?.instance).toBeUndefined();
    expect(result.frontEndError).toBe('');
  });

  it('keeps the current classic instance when an obsolete render completes after returning to TABLE', async () => {
    const previous = pendingResult<HeliumClassicVisualization | undefined>();
    const current = pendingResult<HeliumClassicVisualization | undefined>();
    const destroyInstance = vi.fn();
    const result = component(
      {},
      {
        createClassicVisualization: vi.fn().mockReturnValueOnce(previous.promise).mockReturnValueOnce(current.promise),
        destroyInstance
      }
    );
    result.id = 'paragraph';
    result.visualizations.push({
      id: 'classic',
      name: 'classic',
      isClassic: true,
      icon: {},
      Class: vi.fn() as unknown as HeliumClassicVisualizationConstructor,
      instance: undefined,
      changeSubscription: null
    });
    result.config = { graph: { ...new GraphConfig(), mode: 'classic' } };
    result.result = { type: DatasetType.TABLE, data: 'column\nprevious' };
    result.renderDefaultDisplay();
    result.result = { type: DatasetType.NULL, data: '' };
    result.renderDefaultDisplay();
    result.result = { type: DatasetType.TABLE, data: 'column\ncurrent' };
    result.renderDefaultDisplay();
    const currentInstance = {} as HeliumClassicVisualization;
    const previousInstance = {} as HeliumClassicVisualization;

    current.resolve(currentInstance);
    await flushCompilation();
    previous.resolve(previousInstance);
    await flushCompilation();

    expect(result.visualizations.at(-1)?.instance).toBe(currentInstance);
    expect(destroyInstance).toHaveBeenCalledExactlyOnceWith('pparagraph_classic', false, previousInstance);
  });

  it.each([DatasetType.SVG, DatasetType.NULL])(
    'removes classic visualization containers for %s in the template',
    async type => {
      await TestBed.configureTestingModule({
        declarations: [NotebookParagraphResultComponent],
        imports: [CommonModule, PortalModule],
        schemas: [NO_ERRORS_SCHEMA],
        providers: [
          provideZoneChangeDetection(),
          { provide: RuntimeCompilerService, useValue: {} },
          { provide: NgZService, useValue: { contextChanged: () => EMPTY } },
          { provide: HeliumService, useValue: { visualizationBundles: () => EMPTY } },
          { provide: ClassicVisualizationService, useValue: { destroyAllInstances: vi.fn() } }
        ]
      })
        .overrideComponent(NotebookParagraphResultComponent, {
          set: { template, templateUrl: undefined, styles: [], styleUrls: [] }
        })
        .compileComponents();
      const fixture = TestBed.createComponent(NotebookParagraphResultComponent);
      const result = fixture.componentInstance;
      result.published = true;
      result.id = 'paragraph';
      result.visualizations.push({
        id: 'classic',
        name: 'classic',
        isClassic: true,
        icon: {},
        Class: vi.fn() as unknown as HeliumClassicVisualizationConstructor,
        instance: undefined,
        changeSubscription: null
      });
      result.config = { graph: { ...new GraphConfig(), mode: 'classic' } };
      result.result = { type: DatasetType.TABLE, data: 'column\nvalue' };
      vi.spyOn(result, 'renderGraph').mockImplementation(() => {});
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      const containers = '.classic-visualization-container, .transformation-setting, .visualization-setting';
      expect(element.querySelectorAll(containers)).toHaveLength(3);

      result.updateResult(result.config, { type, data: '<svg />' });
      fixture.detectChanges();

      expect(element.querySelectorAll(containers)).toHaveLength(0);
      expect(element.querySelectorAll('img')).toHaveLength(type === DatasetType.SVG ? 1 : 0);
    }
  );
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
      {} as NgZService,
      {} as HeliumService,
      {} as ClassicVisualizationService
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
