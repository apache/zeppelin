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

import { AnyExposedModule, ReactMountHandle, ReactProps } from './react-mount-handle';
import { ReactMountDirective } from './react-mount.directive';
import { ReactRemoteLoaderService } from './react-remote-loader.service';

describe('ReactMountDirective', () => {
  it('re-enters the Angular zone when a remote invokes onError directly', async () => {
    const host = new ElementRef(document.createElement('div'));
    const ngZone = new NgZone({ enableLongStackTrace: false });
    const expectedError = new Error('remote failed');
    let mountedProps: ReactProps | null = null;
    let callbackRanInAngularZone = false;

    const handle: ReactMountHandle = {
      update: vi.fn(),
      unmount: vi.fn()
    };
    const exposedModule: AnyExposedModule = {
      mount: (_element: HTMLElement, props: ReactProps): ReactMountHandle => {
        mountedProps = props;
        return handle;
      }
    };
    const loader = {
      loadModule: vi.fn(async () => exposedModule)
    };
    const directive = new ReactMountDirective(host, ngZone, loader as unknown as ReactRemoteLoaderService);

    directive.module = './ParagraphFooter';
    directive.reactProps = {
      onError: (error: unknown) => {
        expect(error).toBe(expectedError);
        callbackRanInAngularZone = NgZone.isInAngularZone();
      }
    };

    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true),
      reactProps: new SimpleChange(undefined, directive.reactProps, true)
    });

    await vi.waitFor(() => {
      expect(mountedProps).not.toBeNull();
    });

    const onError = mountedProps?.onError;
    expect(onError).toEqual(expect.any(Function));

    ngZone.runOutsideAngular(() => {
      onError?.(expectedError);
    });

    expect(callbackRanInAngularZone).toBe(true);
  });
});
