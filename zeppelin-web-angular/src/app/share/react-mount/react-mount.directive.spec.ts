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

import { ElementRef, NgZone, SimpleChange } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';

import { ReactRemoteLoaderService } from './react-remote-loader.service';
import { ReactExposedModule, ReactHostCallbacks, ReactMountHandle, ReactProps } from './react-mount-handle';
import { ReactMountDirective } from './react-mount.directive';

describe('ReactMountDirective', () => {
  it('mounts React remotes outside the Angular zone without TestBed', async () => {
    const host = new ElementRef<HTMLElement>(document.createElement('div'));
    const ngZone = new NgZone({});
    const mountedOutsideZone: boolean[] = [];
    let insideRunOutsideAngular = false;
    const unmount = vi.fn();
    const mountHandle: ReactMountHandle = {
      update: vi.fn(),
      unmount
    };
    const remote: ReactExposedModule = {
      mount: (_element: HTMLElement, _props: ReactProps) => {
        mountedOutsideZone.push(insideRunOutsideAngular);
        return mountHandle;
      }
    };
    const loadModule = vi.fn(async <T>(): Promise<T> => remote as T);
    const loader = { loadModule } as Pick<ReactRemoteLoaderService, 'loadModule'>;
    // zone.js cannot patch the native async/await vitest emits.
    // isInAngularZone() is therefore always false past the await.
    const runOutsideAngular = ngZone.runOutsideAngular.bind(ngZone);
    vi.spyOn(ngZone, 'runOutsideAngular').mockImplementation((fn: () => unknown) => {
      insideRunOutsideAngular = true;
      try {
        return runOutsideAngular(fn);
      } finally {
        insideRunOutsideAngular = false;
      }
    });
    const directive = new ReactMountDirective(host, ngZone, loader as ReactRemoteLoaderService);

    directive.module = 'paragraph-footer';
    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true)
    });
    await vi.waitFor(() => expect(mountedOutsideZone).toHaveLength(1));

    expect(loadModule).toHaveBeenCalledWith('paragraph-footer');
    expect(mountedOutsideZone).toEqual([true]);

    directive.ngOnDestroy();

    expect(unmount).toHaveBeenCalledOnce();
  });

  it('re-enters the Angular zone for callbacks invoked by the React remote', async () => {
    const host = new ElementRef<HTMLElement>(document.createElement('div'));
    const ngZone = new NgZone({});
    let mountedProps: (ReactProps & ReactHostCallbacks) | undefined;
    let updatedProps: (ReactProps & ReactHostCallbacks) | undefined;
    const update = vi.fn((props: ReactProps & ReactHostCallbacks) => {
      updatedProps = props;
    });
    const mountHandle: ReactMountHandle = {
      update,
      unmount: vi.fn()
    };
    const remote: ReactExposedModule = {
      mount: (_element: HTMLElement, props: ReactProps & ReactHostCallbacks) => {
        mountedProps = props;
        return mountHandle;
      }
    };
    const loadModule = vi.fn(async <T>(): Promise<T> => remote as T);
    const loader = { loadModule } as Pick<ReactRemoteLoaderService, 'loadModule'>;
    const zoneStates: boolean[] = [];
    const onMountError = vi.fn(() => {
      zoneStates.push(NgZone.isInAngularZone());
    });
    const onUpdateError = vi.fn(() => {
      zoneStates.push(NgZone.isInAngularZone());
    });
    const directive = new ReactMountDirective(host, ngZone, loader as ReactRemoteLoaderService);

    directive.module = 'paragraph-footer';
    directive.reactProps = { onError: onMountError };
    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true),
      reactProps: new SimpleChange(undefined, directive.reactProps, true)
    });
    await vi.waitFor(() => expect(mountedProps).toBeDefined());

    ngZone.runOutsideAngular(() => {
      mountedProps!.onError!(new Error('mount remote failed'));
    });

    directive.reactProps = { onError: onUpdateError };
    directive.ngOnChanges({
      reactProps: new SimpleChange({ onError: onMountError }, directive.reactProps, false)
    });

    ngZone.runOutsideAngular(() => {
      updatedProps!.onError!(new Error('update remote failed'));
    });

    expect(onMountError).toHaveBeenCalledOnce();
    expect(onUpdateError).toHaveBeenCalledOnce();
    expect(update).toHaveBeenCalledOnce();
    expect(zoneStates).toEqual([true, true]);
  });
});
