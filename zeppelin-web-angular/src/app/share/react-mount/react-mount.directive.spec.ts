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
import { ReactExposedModule, ReactMountHandle, ReactProps } from './react-mount-handle';
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
});
