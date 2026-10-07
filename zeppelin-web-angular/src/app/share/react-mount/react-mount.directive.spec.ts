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

const callbackFrom = (props: ReactProps | undefined, name: string) => {
  const callback = props?.[name];
  if (typeof callback !== 'function') {
    throw new Error(`Remote did not receive the ${name} callback`);
  }
  return callback;
};

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
    const loader = new ReactRemoteLoaderService();
    vi.spyOn(loader, 'loadModule').mockResolvedValue(remote);
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
    const directive = new ReactMountDirective(host, ngZone, loader);

    directive.module = 'paragraph-footer';
    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true)
    });
    await vi.waitFor(() => expect(mountedOutsideZone).toHaveLength(1));

    expect(loader.loadModule).toHaveBeenCalledWith('paragraph-footer');
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
    const loader = new ReactRemoteLoaderService();
    vi.spyOn(loader, 'loadModule').mockResolvedValue(remote);
    const zoneStates: boolean[] = [];
    const onMountError = vi.fn(() => {
      zoneStates.push(NgZone.isInAngularZone());
    });
    const onUpdateError = vi.fn(() => {
      zoneStates.push(NgZone.isInAngularZone());
    });
    const directive = new ReactMountDirective(host, ngZone, loader);

    directive.module = 'paragraph-footer';
    directive.reactProps = { onError: onMountError };
    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true),
      reactProps: new SimpleChange(undefined, directive.reactProps, true)
    });
    await vi.waitFor(() => expect(mountedProps).toBeDefined());

    ngZone.runOutsideAngular(() => {
      callbackFrom(mountedProps, 'onError')(new Error('mount remote failed'));
    });

    directive.reactProps = { onError: onUpdateError };
    directive.ngOnChanges({
      reactProps: new SimpleChange({ onError: onMountError }, directive.reactProps, false)
    });

    ngZone.runOutsideAngular(() => {
      callbackFrom(updatedProps, 'onError')(new Error('update remote failed'));
    });

    expect(onMountError).toHaveBeenCalledOnce();
    expect(onUpdateError).toHaveBeenCalledOnce();
    expect(update).toHaveBeenCalledOnce();
    expect(zoneStates).toEqual([true, true]);
  });

  it('re-enters the zone for host callbacks other than onError', async () => {
    const host = new ElementRef<HTMLElement>(document.createElement('div'));
    const ngZone = new NgZone({});
    let mountedProps: (ReactProps & ReactHostCallbacks) | undefined;
    const remote: ReactExposedModule = {
      mount: (_element: HTMLElement, props: ReactProps & ReactHostCallbacks) => {
        mountedProps = props;
        return { update: vi.fn(), unmount: vi.fn() };
      }
    };
    const loader = new ReactRemoteLoaderService();
    vi.spyOn(loader, 'loadModule').mockResolvedValue(remote);
    const zoneStates: boolean[] = [];
    const received: unknown[] = [];
    // A surface that hands the remote a real callback, the way the notebook
    // repository list does with its save. Left unwrapped, the host's refetch
    // and any HTTP it starts would run outside NgZone.
    const onRepoChange = vi.fn((repo: unknown) => {
      zoneStates.push(NgZone.isInAngularZone());
      received.push(repo);
    });
    const directive = new ReactMountDirective(host, ngZone, loader);

    directive.module = 'notebook-repos';
    directive.reactProps = { onRepoChange };
    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true),
      reactProps: new SimpleChange(undefined, directive.reactProps, true)
    });
    await vi.waitFor(() => expect(mountedProps).toBeDefined());

    ngZone.runOutsideAngular(() => {
      callbackFrom(mountedProps, 'onRepoChange')({ name: 'GitNotebookRepo' });
    });

    expect(zoneStates).toEqual([true]);
    expect(received).toEqual([{ name: 'GitNotebookRepo' }]);
  });

  it('keeps non-function props as they are', async () => {
    const host = new ElementRef<HTMLElement>(document.createElement('div'));
    const ngZone = new NgZone({});
    let mountedProps: (ReactProps & ReactHostCallbacks) | undefined;
    const remote: ReactExposedModule = {
      mount: (_element: HTMLElement, props: ReactProps & ReactHostCallbacks) => {
        mountedProps = props;
        return { update: vi.fn(), unmount: vi.fn() };
      }
    };
    const loader = new ReactRemoteLoaderService();
    vi.spyOn(loader, 'loadModule').mockResolvedValue(remote);
    const repositories = [{ name: 'GitNotebookRepo' }];
    const directive = new ReactMountDirective(host, ngZone, loader);

    directive.module = 'notebook-repos';
    directive.reactProps = { repositories, readOnly: false };
    directive.ngOnChanges({
      module: new SimpleChange(undefined, directive.module, true),
      reactProps: new SimpleChange(undefined, directive.reactProps, true)
    });
    await vi.waitFor(() => expect(mountedProps).toBeDefined());

    // Same references, so the remote can still memoize on them.
    expect(mountedProps?.repositories).toBe(repositories);
    expect(mountedProps?.readOnly).toBe(false);
  });
  it('returns what a host callback returns', async () => {
    const ngZone = new NgZone({});
    let mountedProps: ReactProps | undefined;
    const remote: ReactExposedModule = {
      mount: (_element, props) => {
        mountedProps = props;
        return { update: vi.fn(), unmount: vi.fn() };
      }
    };
    const loader = new ReactRemoteLoaderService();
    vi.spyOn(loader, 'loadModule').mockResolvedValue(remote);
    const directive = new ReactMountDirective(new ElementRef(document.createElement('div')), ngZone, loader);
    const unsubscribe = vi.fn();
    directive.module = './AssistantWorkspace';
    directive.reactProps = { subscribePanelClose: () => unsubscribe };
    directive.ngOnChanges({ module: new SimpleChange(undefined, directive.module, true) });
    await vi.waitFor(() => expect(mountedProps).toBeDefined());
    expect(ngZone.runOutsideAngular(() => callbackFrom(mountedProps, 'subscribePanelClose')())).toBe(unsubscribe);
    directive.ngOnDestroy();
  });

  it('logs synchronous host callback failures by default and keeps wrapper identity stable', async () => {
    const ngZone = new NgZone({});
    const update = vi.fn();
    let mountedProps: ReactProps | undefined;
    const remote: ReactExposedModule = {
      mount: (_element, props) => {
        mountedProps = props;
        return { update, unmount: vi.fn() };
      }
    };
    const loader = new ReactRemoteLoaderService();
    vi.spyOn(loader, 'loadModule').mockResolvedValue(remote);
    const directive = new ReactMountDirective(new ElementRef(document.createElement('div')), ngZone, loader);
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const stable = vi.fn(() => 'value');
    const failing = () => {
      throw new Error('host failed');
    };
    directive.module = './ParagraphFooter';
    directive.reactProps = { stable, failing };
    directive.ngOnChanges({ module: new SimpleChange(undefined, directive.module, true) });
    await vi.waitFor(() => expect(mountedProps).toBeDefined());

    expect(callbackFrom(mountedProps, 'stable')()).toBe('value');
    expect(() => callbackFrom(mountedProps, 'failing')()).not.toThrow();
    expect(consoleError).toHaveBeenCalledWith('[ReactMountDirective] host callback "failing" threw', expect.any(Error));

    const firstStable = callbackFrom(mountedProps, 'stable');
    directive.reactProps = { stable, failing, label: 'next' };
    directive.ngOnChanges({ reactProps: new SimpleChange(undefined, directive.reactProps, false) });
    expect(update).toHaveBeenLastCalledWith(expect.objectContaining({ stable: firstStable, label: 'next' }));
    consoleError.mockRestore();
    directive.ngOnDestroy();
  });
});
