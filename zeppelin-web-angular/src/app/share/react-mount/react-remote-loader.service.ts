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

import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ReactExposedModule } from './react-mount-handle';

interface RemoteContainer {
  get<T>(key: string): Promise<() => T>;
  init?: (shareScope: unknown) => Promise<void>;
}

declare global {
  interface Window {
    reactApp?: RemoteContainer;
  }
}

@Injectable({ providedIn: 'root' })
export class ReactRemoteLoaderService {
  private containerPromise: Promise<RemoteContainer> | null = null;
  private readonly modulePromises = new Map<string, Promise<ReactExposedModule>>();

  loadContainer(): Promise<RemoteContainer> {
    if (this.containerPromise) {
      return this.containerPromise;
    }

    this.containerPromise = new Promise<RemoteContainer>((resolve, reject) => {
      if (window.reactApp) {
        resolve(window.reactApp);
        return;
      }

      const script = document.createElement('script');
      script.src = environment.reactRemoteEntryUrl;
      script.async = true;

      const timeoutMs = environment.reactRemoteLoadTimeoutMs;
      let timer: ReturnType<typeof setTimeout> | undefined;

      // Remove the tag on *any* failure (network error, timeout, or
      // loaded-but-unregistered): containerPromise resets on rejection, so each
      // retry would otherwise leak a tag.
      const fail = (message: string) => {
        clearTimeout(timer);
        script.remove();
        reject(new Error(message));
      };

      script.onload = () => {
        clearTimeout(timer);
        if (!window.reactApp) {
          fail('window.reactApp not registered after script load');
          return;
        }
        resolve(window.reactApp);
      };
      script.onerror = () => fail(`Failed to load React remote at ${script.src}`);

      // A request the server accepts but never answers fires neither onload nor
      // onerror, so without this the promise stays pending for minutes.
      if (timeoutMs > 0) {
        timer = setTimeout(
          () => fail(`Timed out after ${timeoutMs} ms loading the React remote at ${script.src}`),
          timeoutMs
        );
      }

      document.head.appendChild(script);
    });

    // Clear the container promise AND drain module cache on failure so a
    // future caller can retry. A stale failed container would otherwise
    // poison every module fetch.
    this.containerPromise.catch(() => {
      this.containerPromise = null;
      this.modulePromises.clear();
    });

    return this.containerPromise;
  }

  loadModule<T extends ReactExposedModule>(exposedKey: string): Promise<T> {
    const cached = this.modulePromises.get(exposedKey);
    if (cached) {
      return cached as Promise<T>;
    }

    const promise = (async () => {
      const container = await this.loadContainer();
      const factory = await container.get<T>(exposedKey);
      return factory();
    })();

    this.modulePromises.set(exposedKey, promise);

    // Evict failed module promise so the next caller can retry.
    promise.catch(() => {
      this.modulePromises.delete(exposedKey);
    });

    return promise;
  }
}
