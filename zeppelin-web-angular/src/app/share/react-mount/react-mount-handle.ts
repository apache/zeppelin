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

export type ReactProps = Record<string, unknown>;

export interface ReactHostCallbacks {
  onError?: (error: unknown) => void;
}

export interface ReactMountHandle {
  update: (props: ReactProps & ReactHostCallbacks) => void;
  unmount: () => void;
}

export type ReactMountFn = (element: HTMLElement, props: ReactProps & ReactHostCallbacks) => ReactMountHandle;

/**
 * Shape of a Module Federation exposed module: a factory returning an
 * object whose `mount` is the entry point.
 */
export interface ReactExposedModule {
  mount: ReactMountFn;
}

/**
 * Legacy shape (used by ./PublishedParagraph until its follow-up
 * refactor): mount returns a bare unmount function.
 */
export type LegacyMountFn = (element: HTMLElement, props: ReactProps) => () => void;

export interface LegacyExposedModule {
  mount: LegacyMountFn;
}

export type AnyExposedModule = ReactExposedModule | LegacyExposedModule;
