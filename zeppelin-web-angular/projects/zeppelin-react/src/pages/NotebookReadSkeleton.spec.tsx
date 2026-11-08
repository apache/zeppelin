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

import { act, Profiler } from 'react';
import { render, screen } from '@testing-library/react';
import { createNotebookReadStore, type NotebookReadPort, type NotebookReadRequest } from '@zeppelin/notebook-core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount, NotebookReadSkeleton, NotebookReadSkeletonMountHandle } from './NotebookReadSkeleton';

// The port as NotebookCoreReadHost builds it: a frozen view of the actual read store.
// `notifyUnchanged` calls its listeners without a change, which a port is allowed to do.
const createHostPort = () => {
  const store = createNotebookReadStore();
  let listeners = 0;
  const subscribed = new Set<() => void>();
  const core: NotebookReadPort = Object.freeze({
    getSnapshot: () => store.getSnapshot(),
    subscribe: (listener: () => void) => {
      listeners += 1;
      subscribed.add(listener);
      const unsubscribe = store.subscribe(listener);
      return () => {
        listeners -= 1;
        subscribed.delete(listener);
        unsubscribe();
      };
    }
  });
  const notifyUnchanged = () => subscribed.forEach(listener => listener());
  return { store, core, listeners: () => listeners, notifyUnchanged };
};

const note = (id: string) => ({ id, name: `${id} name`, path: `/${id}`, paragraphs: [] });

const status = () => screen.getByRole('status');

let element: HTMLElement;
let handle: NotebookReadSkeletonMountHandle | undefined;

const mountSkeleton = (props: Parameters<typeof mount>[1]) => {
  element = document.createElement('div');
  document.body.appendChild(element);
  act(() => {
    handle = mount(element, props);
  });
  return handle!;
};

// React reports a render error to console.error and, in development, as a window error event
// that jsdom would print as uncaught. Both are expected when a failure is injected.
const expectingRenderErrors = (run: () => void) => {
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
  const preventReport = (event: ErrorEvent) => event.preventDefault();
  window.addEventListener('error', preventReport);
  try {
    run();
  } finally {
    window.removeEventListener('error', preventReport);
    consoleError.mockRestore();
  }
};

afterEach(() => {
  act(() => handle?.unmount());
  handle = undefined;
  element?.remove();
});

describe('NotebookReadSkeleton', () => {
  it('shows every read status the actual read store publishes', () => {
    const port = createHostPort();
    mountSkeleton({ core: port.core });
    const seen = [status().dataset.readStatus];

    let request!: NotebookReadRequest;
    act(() => {
      request = port.store.activate({ kind: 'note', noteId: 'note-a' });
    });
    seen.push(status().dataset.readStatus);
    act(() => request.acceptNote(note('note-a')));
    seen.push(status().dataset.readStatus);
    expect(status().textContent).toBe('Note loaded');

    for (const failure of ['not-found', 'access-denied', 'failed'] as const) {
      act(() => port.store.activate({ kind: 'note', noteId: 'note-b' }).fail(failure));
      seen.push(status().dataset.readStatus);
    }
    act(() => port.store.dispose());
    seen.push(status().dataset.readStatus);

    expect(seen).toEqual(['idle', 'loading', 'ready', 'not-found', 'access-denied', 'failed', 'disposed']);
  });

  it('shows the note, the revision and the paragraph anchor', () => {
    const port = createHostPort();
    port.store.activate({ kind: 'revision', noteId: 'note-a', revisionId: 'revision-1' });

    mountSkeleton({ core: port.core, anchor: 'paragraph-2' });

    expect(screen.getByText('note-a')).toBeTruthy();
    expect(screen.getByText('revision-1')).toBeTruthy();
    expect(screen.getByText('paragraph-2')).toBeTruthy();
  });

  it('does not re-render while the read state is unchanged', () => {
    const port = createHostPort();
    const request = port.store.activate({ kind: 'note', noteId: 'note-a' });
    request.acceptNote(note('note-a'));
    let commits = 0;
    render(
      <Profiler id="skeleton" onRender={() => (commits += 1)}>
        <NotebookReadSkeleton core={port.core} />
      </Profiler>
    );
    const snapshot = port.core.getSnapshot();
    const commitsBefore = commits;

    act(() => {
      request.acceptNote(note('note-a'));
      request.fail('failed');
      port.notifyUnchanged();
    });

    expect(port.core.getSnapshot()).toBe(snapshot);
    expect(commits).toBe(commitsBefore);
  });

  it('releases its store subscription on unmount', () => {
    const port = createHostPort();
    const mounted = mountSkeleton({ core: port.core });
    expect(port.listeners()).toBe(1);

    act(() => mounted.unmount());
    handle = undefined;

    expect(port.listeners()).toBe(0);
  });

  it('moves its subscription to a replacement port', () => {
    const previous = createHostPort();
    const next = createHostPort();
    next.store.activate({ kind: 'note', noteId: 'note-b' });
    const mounted = mountSkeleton({ core: previous.core });

    act(() => mounted.update({ core: next.core }));

    expect(previous.listeners()).toBe(0);
    expect(next.listeners()).toBe(1);
    expect(status().dataset.readStatus).toBe('loading');
  });

  it('reports a completed mount once, also when updates pass a new callback', () => {
    const port = createHostPort();
    const calls: string[] = [];
    const mounted = mountSkeleton({ core: port.core, onMounted: () => calls.push('first') });

    act(() => mounted.update({ core: port.core, onMounted: () => calls.push('second'), anchor: 'paragraph-1' }));
    act(() => mounted.update({ core: port.core, onMounted: () => calls.push('third') }));

    expect(calls).toEqual(['first']);
  });

  it('reports an injected mount failure without completing the mount', () => {
    const onMounted = vi.fn();
    const onError = vi.fn();

    expectingRenderErrors(() => mountSkeleton({ core: createHostPort().core, failure: 'mount', onMounted, onError }));

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onMounted).not.toHaveBeenCalled();
    expect(screen.queryByTestId('notebook-read-skeleton')).toBeNull();
  });

  it('reports an injected failure after the mount completed', () => {
    const calls: string[] = [];

    expectingRenderErrors(() =>
      mountSkeleton({
        core: createHostPort().core,
        failure: 'after-mount',
        onMounted: () => calls.push('mounted'),
        onError: () => calls.push('error')
      })
    );

    expect(calls).toEqual(['mounted', 'error']);
  });
});
