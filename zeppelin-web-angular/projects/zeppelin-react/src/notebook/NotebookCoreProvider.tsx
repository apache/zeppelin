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

import { createContext, ReactNode, useContext, useSyncExternalStore } from 'react';
import type { NotebookCorePort, NotebookCoreRemoteProps, NotebookCoreSnapshot } from '@zeppelin/notebook-core';

const NotebookCoreContext = createContext<NotebookCorePort | null>(null);

export type NotebookCoreProviderProps = NotebookCoreRemoteProps &
  Readonly<{
    children: ReactNode;
  }>;

/**
 * Binds the host-provided port to the React tree. The adapter keeps the exact object it
 * receives: it never creates a Core, wraps the port or copies its state, so the host stays
 * the only owner of notebook state.
 */
export const NotebookCoreProvider = ({ core, children }: NotebookCoreProviderProps) => (
  <NotebookCoreContext.Provider value={core}>{children}</NotebookCoreContext.Provider>
);

/** The host-provided port. Throws outside the provider rather than falling back to a local Core. */
export const useNotebookCore = (): NotebookCorePort => {
  const core = useContext(NotebookCoreContext);
  if (core === null) {
    throw new Error('useNotebookCore must be used inside NotebookCoreProvider');
  }
  return core;
};

/**
 * The current snapshot. React re-renders only when the port returns a different snapshot
 * reference; the port keeps the same reference while state is unchanged.
 */
export const useNotebookSnapshot = (): NotebookCoreSnapshot => {
  const core = useNotebookCore();
  return useSyncExternalStore(core.subscribe, core.getSnapshot);
};

/**
 * A view value derived from the snapshot. React re-renders only when the selected value
 * changes (`Object.is`), so a selector must return a primitive or a value the snapshot
 * already holds, never a new object or array per call.
 */
export const useNotebookSelector = <T,>(selector: (snapshot: NotebookCoreSnapshot) => T): T => {
  const core = useNotebookCore();
  return useSyncExternalStore(core.subscribe, () => selector(core.getSnapshot()));
};
