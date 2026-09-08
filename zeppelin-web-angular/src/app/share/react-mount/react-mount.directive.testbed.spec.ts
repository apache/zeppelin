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

import { Component, NgZone, provideZoneChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';

import { ReactExposedModule, ReactMountHandle, ReactProps } from './react-mount-handle';
import { ReactMountDirective } from './react-mount.directive';
import { ReactRemoteLoaderService } from './react-remote-loader.service';

@Component({
  standalone: false,
  template: `
    <div [zeppelin-react-mount]="module" [reactProps]="reactProps"></div>
  `
})
class HostComponent {
  module = 'paragraph-footer';
  reactProps: ReactProps = { paragraphId: 'p1' };
}

/**
 * Companion to react-mount.directive.spec.ts, which drives the directive by
 * hand. Going through TestBed puts the decorator metadata itself under test:
 * the template bindings and constructor injection have to resolve to get here.
 */
describe('ReactMountDirective (TestBed)', () => {
  let fixture: ComponentFixture<HostComponent>;
  let handle: ReactMountHandle;
  let mountedElements: HTMLElement[];
  let mountZoneStates: boolean[];
  let loadModule: Mock<(module: string) => Promise<ReactExposedModule>>;

  beforeEach(() => {
    mountedElements = [];
    mountZoneStates = [];
    handle = { update: vi.fn(), unmount: vi.fn() };
    const remote: ReactExposedModule = {
      mount: (element: HTMLElement) => {
        mountedElements.push(element);
        mountZoneStates.push(NgZone.isInAngularZone());
        return handle;
      }
    };
    loadModule = vi.fn(async () => remote);

    TestBed.configureTestingModule({
      declarations: [HostComponent, ReactMountDirective],
      // TestBed defaults to zoneless, which would make the zone assertions
      // below pass vacuously. main.ts bootstraps with zones, so mirror it.
      providers: [provideZoneChangeDetection(), { provide: ReactRemoteLoaderService, useValue: { loadModule } }]
    });

    fixture = TestBed.createComponent(HostComponent);
  });

  it('mounts the remote on the host element outside the Angular zone', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    expect(loadModule).toHaveBeenCalledWith('paragraph-footer');
    expect(mountedElements).toEqual([fixture.nativeElement.querySelector('div')]);
    expect(mountZoneStates).toEqual([false]);
  });

  it('forwards later reactProps changes to the mount handle', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.reactProps = { paragraphId: 'p2' };
    fixture.detectChanges();

    expect(handle.update).toHaveBeenCalledWith({ paragraphId: 'p2' });
    expect(loadModule).toHaveBeenCalledOnce();
  });

  it('unmounts when the host component is destroyed', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.destroy();

    expect(handle.unmount).toHaveBeenCalledOnce();
  });
});
