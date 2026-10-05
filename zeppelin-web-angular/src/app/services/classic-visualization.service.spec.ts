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

import { HttpClient } from '@angular/common/http';
import { Injector } from '@angular/core';
import { HeliumClassicVisualization, HeliumClassicVisualizationConstructor } from '@zeppelin/interfaces';
import { GraphConfig } from '@zeppelin/sdk';
import { TableData } from '@zeppelin/visualization';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AngularDragDropService } from './angular-drag-drop.service';
import { BootstrapCompatibilityService } from './bootstrap-compatibility.service';
import { ClassicVisualizationService } from './classic-visualization.service';
import { TableDataAdapterService } from './table-data-adapter.service';

const service = () => {
  const injectBootstrapStyles = vi.fn();
  const instance = new ClassicVisualizationService(
    {} as Injector,
    {} as TableDataAdapterService,
    {} as HttpClient,
    {} as AngularDragDropService,
    { injectBootstrapStyles } as unknown as BootstrapCompatibilityService
  );
  return { instance, injectBootstrapStyles };
};

afterEach(() => {
  document.body.replaceChildren();
  vi.useRealTimers();
});

describe('classic visualization cancellation', () => {
  it('does not destroy a newer instance when an obsolete completion cleans up the same target', () => {
    const { instance } = service();
    const destroy = vi.fn();
    const current = { destroy } as unknown as HeliumClassicVisualization;
    const registry = instance as unknown as {
      activeInstanceInfos: Map<string, { instance: HeliumClassicVisualization }>;
    };
    registry.activeInstanceInfos.set('visualization', { instance: current });

    instance.destroyInstance('visualization', false, {} as HeliumClassicVisualization);

    expect(destroy).not.toHaveBeenCalled();
    expect(registry.activeInstanceInfos.get('visualization')?.instance).toBe(current);
  });
  it('cancels before allocation when its resolved target is removed before the await resumes', async () => {
    const { instance, injectBootstrapStyles } = service();
    const target = document.createElement('div');
    target.id = 'visualization';
    document.body.appendChild(target);
    let active = true;
    const constructor = vi.fn() as unknown as HeliumClassicVisualizationConstructor;
    const pending = instance.createClassicVisualization(
      constructor,
      target.id,
      new GraphConfig(),
      new TableData(),
      vi.fn(),
      () => active
    );

    target.remove();
    active = false;

    await expect(pending).resolves.toBeUndefined();
    expect(injectBootstrapStyles).not.toHaveBeenCalled();
    expect(constructor).not.toHaveBeenCalled();
  });

  it('stops polling without an error when a pending target is cancelled', async () => {
    vi.useFakeTimers();
    const { instance, injectBootstrapStyles } = service();
    let active = true;
    const pending = instance.createClassicVisualization(
      vi.fn() as unknown as HeliumClassicVisualizationConstructor,
      'missing-target',
      new GraphConfig(),
      new TableData(),
      vi.fn(),
      () => active
    );

    active = false;
    await vi.advanceTimersByTimeAsync(100);

    await expect(pending).resolves.toBeUndefined();
    expect(vi.getTimerCount()).toBe(0);
    expect(injectBootstrapStyles).not.toHaveBeenCalled();
  });
});
