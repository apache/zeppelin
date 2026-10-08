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

import { act, StrictMode } from 'react';
import { render, renderHook, screen } from '@testing-library/react';
import type { NotebookCorePort, NotebookCoreSnapshot } from '@zeppelin/notebook-core';
import { describe, expect, it, vi } from 'vitest';
import {
  NotebookCoreProvider,
  useNotebookCore,
  useNotebookSelector,
  useNotebookSnapshot
} from './NotebookCoreProvider';

// A host-owned port as the shell would pass it: a frozen object whose snapshot reference
// changes only when state changes.
const createHostPort = (initial: NotebookCoreSnapshot) => {
  let snapshot = initial;
  const listeners = new Set<() => void>();
  let subscribeCalls = 0;
  const core: NotebookCorePort = Object.freeze({
    getSnapshot: () => snapshot,
    subscribe: (listener: () => void) => {
      subscribeCalls += 1;
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    }
  });
  return {
    core,
    publish: (next: NotebookCoreSnapshot) => {
      snapshot = next;
      act(() => listeners.forEach(listener => listener()));
    },
    notifyWithoutChange: () => {
      act(() => listeners.forEach(listener => listener()));
    },
    listenerCount: () => listeners.size,
    subscribeCalls: () => subscribeCalls
  };
};

const noteSnapshot: NotebookCoreSnapshot = { noteId: 'note-a', revisionId: null };

let renders = 0;
const SnapshotProbe = () => {
  renders += 1;
  const snapshot = useNotebookSnapshot();
  return (
    <span data-testid="snapshot">
      {snapshot.noteId}:{snapshot.revisionId ?? 'live'}
    </span>
  );
};

const renderWithPort = (core: NotebookCorePort) =>
  render(
    <NotebookCoreProvider core={core}>
      <SnapshotProbe />
    </NotebookCoreProvider>
  );

describe('NotebookCoreProvider', () => {
  it('hands the tree the exact port object the host provided', () => {
    const port = createHostPort(noteSnapshot);
    const { result } = renderHook(() => useNotebookCore(), {
      wrapper: ({ children }) => <NotebookCoreProvider core={port.core}>{children}</NotebookCoreProvider>
    });

    expect(result.current).toBe(port.core);
  });

  it('throws outside the provider instead of falling back to a local Core', () => {
    // React reports the render error to console.error and, in development, as a window error
    // event that jsdom would print as uncaught. Both are expected here.
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const preventReport = (event: ErrorEvent) => event.preventDefault();
    window.addEventListener('error', preventReport);
    try {
      expect(() => renderHook(() => useNotebookCore())).toThrow(
        'useNotebookCore must be used inside NotebookCoreProvider'
      );
    } finally {
      window.removeEventListener('error', preventReport);
      consoleError.mockRestore();
    }
  });

  it('renders each snapshot the port publishes', () => {
    const port = createHostPort(noteSnapshot);
    renderWithPort(port.core);
    expect(screen.getByTestId('snapshot').textContent).toBe('note-a:live');

    port.publish({ noteId: 'note-a', revisionId: 'revision-1' });

    expect(screen.getByTestId('snapshot').textContent).toBe('note-a:revision-1');
  });

  it('does not re-render when the port notifies with the same snapshot reference', () => {
    const port = createHostPort(noteSnapshot);
    renderWithPort(port.core);
    const rendersBefore = renders;

    port.notifyWithoutChange();

    expect(renders).toBe(rendersBefore);
  });

  it('removes its subscription on unmount and holds one again after remounting', () => {
    const port = createHostPort(noteSnapshot);
    const first = renderWithPort(port.core);
    expect(port.listenerCount()).toBe(1);

    first.unmount();
    expect(port.listenerCount()).toBe(0);

    renderWithPort(port.core);
    expect(port.listenerCount()).toBe(1);
  });

  it('holds one subscription under StrictMode double mounting', () => {
    const port = createHostPort(noteSnapshot);
    render(
      <StrictMode>
        <NotebookCoreProvider core={port.core}>
          <SnapshotProbe />
        </NotebookCoreProvider>
      </StrictMode>
    );

    expect(port.listenerCount()).toBe(1);
  });

  it('keeps its subscription when re-rendered with the same port', () => {
    const port = createHostPort(noteSnapshot);
    const { rerender } = renderWithPort(port.core);
    const subscribeCallsBefore = port.subscribeCalls();

    rerender(
      <NotebookCoreProvider core={port.core}>
        <SnapshotProbe />
      </NotebookCoreProvider>
    );

    expect(port.subscribeCalls()).toBe(subscribeCallsBefore);
    expect(port.listenerCount()).toBe(1);
  });

  it('moves its subscription to a new port the host passes', () => {
    const previous = createHostPort(noteSnapshot);
    const next = createHostPort({ noteId: 'note-b', revisionId: null });
    const { rerender } = renderWithPort(previous.core);

    rerender(
      <NotebookCoreProvider core={next.core}>
        <SnapshotProbe />
      </NotebookCoreProvider>
    );

    expect(previous.listenerCount()).toBe(0);
    expect(next.listenerCount()).toBe(1);
    expect(screen.getByTestId('snapshot').textContent).toBe('note-b:live');
  });
});

describe('useNotebookSelector', () => {
  it('re-renders only when the selected value changes', () => {
    const port = createHostPort(noteSnapshot);
    let selectorRenders = 0;
    const RevisionProbe = () => {
      selectorRenders += 1;
      return <span data-testid="revision">{useNotebookSelector(snapshot => snapshot.revisionId) ?? 'live'}</span>;
    };
    render(
      <NotebookCoreProvider core={port.core}>
        <RevisionProbe />
      </NotebookCoreProvider>
    );
    const rendersBefore = selectorRenders;

    port.publish({ noteId: 'note-b', revisionId: null });
    expect(selectorRenders).toBe(rendersBefore);

    port.publish({ noteId: 'note-b', revisionId: 'revision-1' });
    expect(selectorRenders).toBe(rendersBefore + 1);
    expect(screen.getByTestId('revision').textContent).toBe('revision-1');
  });
});
