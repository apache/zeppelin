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

import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { ReactRemoteLoaderService } from './react-remote-loader.service';
import {
  AnyExposedModule,
  ReactExposedModule,
  ReactHostCallbacks,
  ReactMountHandle,
  ReactProps
} from './react-mount-handle';

const isLegacyModule = (mod: AnyExposedModule, handleOrUnmount: unknown): handleOrUnmount is () => void => {
  void mod;
  return typeof handleOrUnmount === 'function';
};

const wrapLegacyHandle = (unmount: () => void): ReactMountHandle => ({
  update: () => {
    /* legacy modules don't support updates; no-op */
  },
  unmount
});

@Directive({
  selector: '[zeppelin-react-mount]',
  standalone: false
})
export class ReactMountDirective implements OnChanges, OnDestroy {
  @Input('zeppelin-react-mount') module!: string;
  @Input() reactProps: ReactProps & ReactHostCallbacks = {};

  private latestProps: ReactProps & ReactHostCallbacks = {};
  private destroyed = false;
  private loading = false;
  private handle: ReactMountHandle | null = null;
  private mountedModule: string | null = null;

  constructor(
    private readonly host: ElementRef<HTMLElement>,
    private readonly ngZone: NgZone,
    private readonly loader: ReactRemoteLoaderService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    this.latestProps = this.reactProps ?? {};

    if (changes.module && !changes.module.firstChange && this.mountedModule) {
      // Module swap after first mount is unsupported. Report via onError
      // and otherwise leave the existing handle in place.
      this.reportError(
        new Error(
          `ReactMountDirective: module input changed after mount ` +
            `(from "${this.mountedModule}" to "${this.module}") — unsupported`
        )
      );
      return;
    }

    if (this.handle) {
      this.ngZone.runOutsideAngular(() => {
        try {
          this.handle!.update(this.latestProps);
        } catch (err) {
          this.reportError(err);
        }
      });
      return;
    }

    if (!this.loading && !this.destroyed && this.module) {
      void this.startLoad();
    }
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.handle) {
      try {
        this.handle.unmount();
      } catch (err) {
        this.reportError(err);
      }
      this.handle = null;
    }
  }

  private async startLoad(): Promise<void> {
    this.loading = true;
    const moduleKey = this.module;
    try {
      const mod = await this.loader.loadModule<AnyExposedModule>(moduleKey);
      if (this.destroyed) {
        return;
      }
      this.ngZone.runOutsideAngular(() => {
        try {
          const returned = (mod as ReactExposedModule).mount(this.host.nativeElement, this.latestProps);
          if (isLegacyModule(mod, returned)) {
            this.handle = wrapLegacyHandle(returned as unknown as () => void);
          } else {
            this.handle = returned as ReactMountHandle;
          }
          this.mountedModule = moduleKey;
        } catch (err) {
          this.handle = null;
          this.reportError(err);
        }
      });
    } catch (err) {
      this.reportError(err);
    } finally {
      this.loading = false;
    }
  }

  private reportError(error: unknown): void {
    const onError = this.latestProps.onError;
    if (typeof onError === 'function') {
      try {
        onError(error);
      } catch {
        /* swallow callback errors; they shouldn't loop */
      }
    } else {
      console.error('[ReactMountDirective]', error);
    }
  }
}
