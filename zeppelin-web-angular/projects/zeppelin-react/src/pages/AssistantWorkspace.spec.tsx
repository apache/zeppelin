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
import { fireEvent, render, screen, type RenderResult } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  NOTEBOOK_SIDEBAR_WIDTH,
  type AssistantConnection,
  type AssistantShellProps,
  type AssistantSocketEvent
} from '@zeppelin/sdk';
import { AssistantWorkspace, mount } from './AssistantWorkspace';

const socketHarness = () => {
  const events = new Set<(event: AssistantSocketEvent) => void>();
  const closes = new Set<() => void>();
  return {
    send: vi.fn(),
    subscribe: (listener: (event: AssistantSocketEvent) => void) => {
      events.add(listener);
      return () => events.delete(listener);
    },
    subscribeClose: (listener: () => void) => {
      closes.add(listener);
      return () => closes.delete(listener);
    },
    emit: (event: AssistantSocketEvent) => events.forEach(listener => listener(event)),
    close: () => closes.forEach(listener => listener()),
    consumers: () => [events.size, closes.size]
  };
};

const originalFetch = globalThis.fetch;
let cleanup: (() => Promise<void>) | undefined;

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.reject(new Error('Unexpected Assistant REST request')))
  );
});

afterEach(async () => {
  try {
    await cleanup?.();
  } finally {
    cleanup = undefined;
    globalThis.fetch = originalFetch;
  }
});

const connection = (socket = socketHarness()): AssistantConnection => ({
  apiBase: '/zeppelin/api',
  socket,
  sessionKey: 1,
  onAuthError: vi.fn()
});

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify({ status: 'OK', body }), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });

const mockEmptyConversations = () =>
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, options?: RequestInit) => {
      if (/^\/zeppelin\/api\/notes\/[^/]+\/conversations$/.test(url) && (options?.method ?? 'GET') === 'GET') {
        return Promise.resolve(json([]));
      }
      return Promise.reject(new Error(`Unexpected Assistant REST request: ${options?.method ?? 'GET'} ${url}`));
    })
  );

const conversation = {
  id: 'saved',
  title: 'Saved conversation',
  ownerId: 'alice',
  canSendMessage: true,
  updatedAt: '2026-11-01T00:00:00Z'
};

const question = () => screen.getByRole('textbox', { name: 'Question' }) as HTMLTextAreaElement;
const submit = async (value: string) => {
  await act(async () => {
    fireEvent.change(question(), { target: { value } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
  });
};
const selectSaved = async () => {
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Open conversations' })));
  await act(async () => fireEvent.click(screen.getByRole('button', { name: /^Saved conversation/ })));
};

describe('AssistantWorkspace remote', () => {
  it('portals the navigation and panel, coordinates close, and shares the sidebar width', async () => {
    mockEmptyConversations();
    const root = document.createElement('div');
    const navigation = document.createElement('div');
    const panel = document.createElement('div');
    const onPanelVisibilityChange = vi.fn();
    const onPanelWidthChange = vi.fn();
    let requestClose = () => {};
    const props: AssistantShellProps = {
      noteId: 'note-a',
      connection: connection(),
      paragraphs: [],
      onOpenParagraph: vi.fn(),
      slots: [
        { element: navigation, kind: 'navigation' },
        { element: panel, kind: 'panel' }
      ],
      panelWidth: 370,
      onPanelVisibilityChange,
      onPanelWidthChange,
      subscribePanelClose: listener => {
        requestClose = listener;
        return () => {
          requestClose = () => {};
        };
      }
    };
    let unmountRoot: (() => void) | undefined;
    let updateRoot: ((props: AssistantShellProps) => void) | undefined;
    const unmount = async () => {
      const dispose = unmountRoot;
      unmountRoot = undefined;
      await act(async () => dispose?.());
    };
    cleanup = unmount;

    await act(async () => {
      const handle = mount(root, props);
      unmountRoot = handle.unmount;
      updateRoot = handle.update;
    });
    expect(navigation.querySelector('button')).not.toBeNull();
    expect(panel.textContent).toBe('');

    await act(async () => navigation.querySelector('button')?.click());
    expect(panel.querySelector('aside')).not.toBeNull();
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(true);
    expect(panel.style.getPropertyValue('--assistant-sidebar-width')).toBe('370px');

    await act(async () => {
      panel
        .querySelector<HTMLElement>('[role="separator"]')
        ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    });
    expect(onPanelWidthChange).toHaveBeenCalledWith(386);

    onPanelWidthChange.mockClear();
    const resizeHandle = panel.querySelector<HTMLElement>('[role="separator"]')!;
    await act(async () => fireEvent.pointerDown(resizeHandle, { button: 0, buttons: 1, clientX: 100 }));
    await act(async () => fireEvent.pointerMove(resizeHandle, { buttons: 1, clientX: 140 }));
    expect(panel.style.getPropertyValue('--assistant-sidebar-width')).toBe('426px');
    expect(resizeHandle.getAttribute('aria-valuenow')).toBe('426');
    expect(onPanelWidthChange).not.toHaveBeenCalled();
    await act(async () => fireEvent.pointerUp(resizeHandle, { button: 0, clientX: 140 }));
    expect(onPanelWidthChange).toHaveBeenCalledExactlyOnceWith(426);

    onPanelWidthChange.mockClear();
    await act(async () => fireEvent.pointerDown(resizeHandle, { button: 0, buttons: 1, clientX: 100 }));
    await act(async () => fireEvent.pointerMove(resizeHandle, { buttons: 1, clientX: 120 }));
    await act(async () => fireEvent.pointerMove(resizeHandle, { buttons: 0, clientX: 120 }));
    expect(onPanelWidthChange).toHaveBeenCalledExactlyOnceWith(446);

    await act(async () => requestClose());
    expect(panel.textContent).toBe('');
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(false);

    await act(async () => navigation.querySelector('button')?.click());
    await act(async () => updateRoot!({ ...props, noteId: 'note-b' }));
    expect(panel.textContent).toBe('');

    await unmount();
    expect(navigation.innerHTML).toBe('');
  });
});

describe('AssistantWorkspace', () => {
  const setup = async (overrides: Partial<AssistantShellProps> = {}) => {
    const navigation = document.createElement('div');
    const panel = document.createElement('div');
    document.body.append(navigation, panel);
    const onPanelVisibilityChange = vi.fn();
    const onPanelWidthChange = vi.fn();
    let unmountRoot: (() => void) | undefined;
    let updateRoot: ((props: AssistantShellProps) => void) | undefined;
    let props: AssistantShellProps = {
      noteId: 'note-a',
      connection: connection(),
      paragraphs: [],
      onOpenParagraph: vi.fn(),
      slots: [
        { element: navigation, kind: 'navigation' },
        { element: panel, kind: 'panel' }
      ],
      panelWidth: 370,
      onPanelVisibilityChange,
      onPanelWidthChange,
      subscribePanelClose: () => () => {},
      ...overrides
    };
    const unmount = async () => {
      const dispose = unmountRoot;
      unmountRoot = undefined;
      try {
        await act(async () => dispose?.());
      } finally {
        navigation.remove();
        panel.remove();
      }
    };
    cleanup = unmount;
    await act(async () => {
      const handle = mount(document.createElement('div'), props);
      unmountRoot = handle.unmount;
      updateRoot = handle.update;
    });
    expect(screen.getByRole('button', { name: 'Toggle AI Assistant' })).toBeTruthy();
    const toggle = () => screen.getByRole('button', { name: 'Toggle AI Assistant' });
    const open = async () => act(async () => fireEvent.click(toggle()));
    const update = async (changes: Partial<AssistantShellProps>) => {
      props = { ...props, ...changes };
      await act(async () => updateRoot!(props));
    };
    return { toggle, open, unmount, update, onPanelVisibilityChange, onPanelWidthChange };
  };

  it('keeps the latest conversation list during StrictMode replay and ignores auth errors after close', async () => {
    const obsolete = Promise.withResolvers<Response>();
    const current = Promise.withResolvers<Response>();
    const history = Promise.withResolvers<Response>();
    const host = connection();
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockReturnValueOnce(obsolete.promise)
        .mockReturnValueOnce(current.promise)
        .mockReturnValueOnce(history.promise)
    );
    const navigation = document.createElement('div');
    const panel = document.createElement('div');
    document.body.append(navigation, panel);
    let rendered: RenderResult | undefined;
    cleanup = async () => {
      try {
        rendered?.unmount();
      } finally {
        navigation.remove();
        panel.remove();
      }
    };
    await act(async () => {
      rendered = render(
        <StrictMode>
          <AssistantWorkspace
            noteId="note-a"
            connection={host}
            paragraphs={[]}
            onOpenParagraph={vi.fn()}
            slots={[
              { element: navigation, kind: 'navigation' },
              { element: panel, kind: 'panel' }
            ]}
            panelWidth={370}
            onPanelWidthChange={vi.fn()}
            onPanelVisibilityChange={vi.fn()}
            subscribePanelClose={() => () => {}}
          />
        </StrictMode>
      );
    });
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Toggle AI Assistant' })));
    await act(async () => {
      current.resolve(json([conversation]));
      obsolete.resolve(json([{ ...conversation, id: 'old', title: 'Obsolete conversation' }]));
    });
    await selectSaved();
    expect(screen.queryByText('Obsolete conversation')).toBeNull();
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
    await act(async () => history.resolve(new Response(null, { status: 401 })));
    expect(host.onAuthError).not.toHaveBeenCalled();
    expect(screen.queryByRole('complementary', { name: 'AI Assistant workspace' })).toBeNull();
  });

  it('preserves the draft when the host visibility callback changes and notifies the latest host on close', async () => {
    mockEmptyConversations();
    const { open, update, onPanelVisibilityChange } = await setup();
    await open();
    await act(async () => fireEvent.change(question(), { target: { value: 'Keep this draft' } }));
    const latestHost = vi.fn();
    await update({ onPanelVisibilityChange: latestHost });
    expect(question().value).toBe('Keep this draft');
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(true);
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
    expect(latestHost).toHaveBeenLastCalledWith(false);
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(true);
  });

  it('uses the host REST and socket by default and resolves saved and streaming paragraph references', async () => {
    const paragraphId = 'paragraph_1423500782552_-1439281894';
    const unknownId = 'paragraph_1423500782553_1439281894';
    const socket = socketHarness();
    const host = connection(socket);
    const onOpenParagraph = vi.fn();
    const fetch = vi.fn((url: string) =>
      Promise.resolve(
        url.includes('/messages')
          ? json({
              messages: [{ id: 'answer', role: 'assistant', content: `Saved: ${paragraphId} and ${unknownId}` }],
              cursor: null
            })
          : json([conversation])
      )
    );
    vi.stubGlobal('fetch', fetch);
    const { open, update } = await setup({
      connection: host,
      noteId: 'note/a',
      paragraphs: [{ id: paragraphId, title: 'Sales query' }],
      onOpenParagraph
    });
    expect(fetch).not.toHaveBeenCalled();
    await open();
    await selectSaved();

    expect(fetch.mock.calls.map(([url]) => url)).toEqual(
      expect.arrayContaining([
        '/zeppelin/api/notes/note%2Fa/conversations',
        '/zeppelin/api/notes/note%2Fa/conversations/saved/messages?limit=50'
      ])
    );
    expect(screen.getByText(new RegExp(unknownId))).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' }));
    expect(onOpenParagraph).toHaveBeenCalledExactlyOnceWith(paragraphId);

    await submit('Explain this query');
    expect(socket.send).toHaveBeenCalledExactlyOnceWith({
      noteId: 'note/a',
      conversationId: 'saved',
      content: 'Explain this query'
    });
    await act(async () => {
      socket.emit({ conversationId: 'saved', type: 'run.started', payload: { runId: 'run-1' } });
      socket.emit({
        conversationId: 'saved',
        type: 'message.delta',
        payload: { messageId: 'stream', delta: `Streaming: ${paragraphId}` }
      });
    });
    expect(screen.getAllByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' })).toHaveLength(2);
    await act(async () => fireEvent.change(question(), { target: { value: 'Keep the next draft' } }));
    const updatedCallback = vi.fn();
    await update({
      panelWidth: 400,
      paragraphs: [{ id: paragraphId, title: 'Updated query' }],
      onOpenParagraph: updatedCallback
    });
    expect(question().value).toBe('Keep the next draft');
    expect(socket.send).toHaveBeenCalledTimes(1);
    const references = screen.getAllByRole('button', { name: 'Paragraph 1: show Updated query in the notebook' });
    fireEvent.click(references[1]);
    expect(updatedCallback).toHaveBeenCalledExactlyOnceWith(paragraphId);
    expect(onOpenParagraph).toHaveBeenCalledTimes(1);
  });

  it('retains a confirmed conversation after its first socket submission fails and retries without creating again', async () => {
    const socket = socketHarness();
    const fetch = vi.fn().mockResolvedValueOnce(json([])).mockResolvedValueOnce(json(conversation, 201));
    vi.stubGlobal('fetch', fetch);
    const { open } = await setup({ connection: connection(socket) });
    await open();
    await submit('Keep this question');
    await act(async () =>
      socket.emit({
        conversationId: 'saved',
        type: 'run.failed',
        payload: { error: { status: 429 } }
      })
    );
    expect(question().value).toBe('Keep this question');
    expect(screen.getByText('The assistant is busy. Try again shortly.')).toBeTruthy();
    expect(socket.consumers()).toEqual([0, 0]);

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Retry' })));
    expect(socket.send.mock.calls).toEqual([
      [{ noteId: 'note-a', conversationId: 'saved', content: 'Keep this question' }],
      [{ noteId: 'note-a', conversationId: 'saved', content: 'Keep this question' }]
    ]);
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toEqual([
      ['/zeppelin/api/notes/note-a/conversations', expect.objectContaining({ method: 'POST', body: '{}' })]
    ]);
  });

  it.each(['close', 'note', 'session', 'unmount'] as const)(
    'disposes the real socket consumer on %s and never shows its late answer in a new panel',
    async change => {
      const socket = socketHarness();
      const host = connection(socket);
      vi.stubGlobal(
        'fetch',
        vi.fn((_url: string, options?: RequestInit) =>
          Promise.resolve(options?.method === 'POST' ? json(conversation, 201) : json([]))
        )
      );
      const { open, update, unmount } = await setup({ connection: host });
      await open();
      await submit('Old question');
      await act(async () => {
        socket.emit({ conversationId: 'saved', type: 'run.started', payload: {} });
        socket.emit({
          conversationId: 'saved',
          type: 'message.delta',
          payload: { messageId: 'old', delta: 'Previous session answer' }
        });
      });
      expect(screen.getByText('Previous session answer')).toBeTruthy();

      switch (change) {
        case 'close':
          await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
          break;
        case 'note':
          await update({ noteId: 'note-b' });
          break;
        case 'session':
          await update({ connection: { ...host, sessionKey: 2 } });
          break;
        case 'unmount':
          await unmount();
          break;
      }
      expect(socket.consumers()).toEqual([0, 0]);
      expect(screen.queryByText('Previous session answer')).toBeNull();
      await act(async () =>
        socket.emit({
          conversationId: 'saved',
          type: 'message.delta',
          payload: { messageId: 'old', delta: 'Late old answer' }
        })
      );
      if (change !== 'unmount') await open();
      expect(screen.queryByText(/Previous session answer|Late old answer/)).toBeNull();
      expect(socket.send).toHaveBeenCalledTimes(1);
    }
  );

  it.each(['close', 'note', 'session', 'unmount'] as const)(
    'ignores late authentication failures after %s while forwarding current failures to the host',
    async change => {
      const pending = Promise.withResolvers<Response>();
      const host = connection();
      const fetch = vi
        .fn()
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(new Response(null, { status: 405, headers: { Location: '/current-login' } }));
      vi.stubGlobal('fetch', fetch);
      const { open, update, unmount } = await setup({ connection: host });
      await open();
      switch (change) {
        case 'close':
          await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
          break;
        case 'note':
          await update({ noteId: 'note-b' });
          break;
        case 'session':
          await update({ connection: { ...host, sessionKey: 2 } });
          break;
        case 'unmount':
          await unmount();
          break;
      }
      await act(async () =>
        pending.resolve(new Response(null, { status: 401, headers: { Location: '/obsolete-login' } }))
      );
      expect(host.onAuthError).not.toHaveBeenCalled();
      if (change !== 'unmount') await open();
      expect(vi.mocked(host.onAuthError).mock.calls).toEqual(change === 'unmount' ? [] : [[405, '/current-login']]);
      expect(screen.queryByText(/obsolete-login/)).toBeNull();
    }
  );

  it.each(['close', 'note', 'session', 'unmount'] as const)('ignores late history data after %s', async change => {
    const pending = Promise.withResolvers<Response>();
    const host = connection();
    const fetch = vi.fn((url: string) =>
      url.includes('/messages') ? pending.promise : Promise.resolve(json([conversation]))
    );
    vi.stubGlobal('fetch', fetch);
    const { open, update, unmount } = await setup({ connection: host });
    await open();
    await selectSaved();
    switch (change) {
      case 'close':
        await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
        break;
      case 'note':
        await update({ noteId: 'note-b' });
        break;
      case 'session':
        await update({ connection: { ...host, sessionKey: 2 } });
        break;
      case 'unmount':
        await unmount();
        break;
    }
    expect(screen.queryByRole('complementary')).toBeNull();
    if (change !== 'unmount') await open();
    await act(async () =>
      pending.resolve(
        json({ messages: [{ id: 'old', role: 'assistant', content: 'Obsolete saved answer' }], cursor: null })
      )
    );
    expect(screen.queryByText('Obsolete saved answer')).toBeNull();
    const field = screen.queryByRole('textbox', { name: 'Question' }) as HTMLTextAreaElement | null;
    expect(field?.value).toBe(change === 'unmount' ? undefined : '');
  });

  it.each(['close', 'note', 'session', 'unmount'] as const)(
    'ignores a late successful creation after %s instead of submitting it',
    async change => {
      const pending = Promise.withResolvers<Response>();
      const socket = socketHarness();
      const host = connection(socket);
      vi.stubGlobal(
        'fetch',
        vi.fn((_url: string, options?: RequestInit) =>
          options?.method === 'POST' ? pending.promise : Promise.resolve(json([]))
        )
      );
      const { open, update, unmount } = await setup({ connection: host });
      await open();
      await submit('Obsolete creation');
      switch (change) {
        case 'close':
          await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
          break;
        case 'note':
          await update({ noteId: 'note-b' });
          break;
        case 'session':
          await update({ connection: { ...host, sessionKey: 2 } });
          break;
        case 'unmount':
          await unmount();
          break;
      }
      if (change !== 'unmount') await open();
      await act(async () => pending.resolve(json(conversation, 201)));
      expect(socket.send).not.toHaveBeenCalled();
      const field = screen.queryByRole('textbox', { name: 'Question' }) as HTMLTextAreaElement | null;
      expect(field?.value).toBe(change === 'unmount' ? undefined : '');
    }
  );

  it('shows a missing conversation from real history without sending', async () => {
    const socket = socketHarness();
    const fetch = vi.fn((url: string) =>
      Promise.resolve(url.includes('/messages') ? new Response(null, { status: 404 }) : json([conversation]))
    );
    vi.stubGlobal('fetch', fetch);
    const { open } = await setup({ connection: connection(socket) });
    await open();
    await selectSaved();
    expect(screen.getByText('This conversation or note no longer exists.')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Send' }) as HTMLButtonElement).disabled).toBe(true);
    expect(socket.send).not.toHaveBeenCalled();
  });

  it('keeps unknown outcome after a real history reload and a subsequent 409 without automatically resending', async () => {
    const socket = socketHarness();
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        Promise.resolve(url.includes('/messages') ? json({ messages: [], cursor: null }) : json([conversation]))
      )
    );
    const { open } = await setup({ connection: connection(socket) });
    await open();
    await selectSaved();
    await submit('Uncertain question');
    await act(async () => socket.close());
    expect(screen.getByText(/The outcome of an earlier question is unknown/)).toBeTruthy();
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Reload conversation' })));
    expect(screen.getByText(/The outcome of an earlier question is unknown/)).toBeTruthy();
    expect(socket.send).toHaveBeenCalledTimes(1);

    await submit('Explicit new submission');
    await act(async () =>
      socket.emit({ conversationId: 'saved', type: 'run.failed', payload: { error: { status: 409 } } })
    );
    expect(screen.getByText('This conversation is still answering. Try again when it finishes.')).toBeTruthy();
    expect(screen.getByText(/The outcome of an earlier question is unknown/)).toBeTruthy();
    expect(question().value).toBe('Explicit new submission');
    expect(socket.send).toHaveBeenCalledTimes(2);
    expect(socket.consumers()).toEqual([0, 0]);
  });

  it('marks the toggle as pressed while the panel is open', async () => {
    mockEmptyConversations();
    const { toggle, open } = await setup();
    expect(toggle().getAttribute('aria-pressed')).toBe('false');

    await open();
    expect(toggle().getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('complementary', { name: 'AI Assistant workspace' })).toBeTruthy();
  });

  it('closes on Escape and returns focus to the toggle', async () => {
    mockEmptyConversations();
    const { toggle, open, onPanelVisibilityChange } = await setup();
    await open();

    await act(async () => fireEvent.keyDown(screen.getByRole('complementary'), { key: 'Escape' }));
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(document.activeElement).toBe(toggle());
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(false);
  });

  it('returns focus to the toggle when the close button is used', async () => {
    mockEmptyConversations();
    const { toggle, open } = await setup();
    await open();

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(document.activeElement).toBe(toggle());
  });

  it('keeps keyboard resizing within the sidebar limits', async () => {
    mockEmptyConversations();
    const { open, onPanelWidthChange } = await setup();
    await open();
    const handle = screen.getByRole('separator', { name: 'Resize AI Assistant panel' });

    await act(async () => fireEvent.keyDown(handle, { key: 'End' }));
    expect(onPanelWidthChange).toHaveBeenLastCalledWith(NOTEBOOK_SIDEBAR_WIDTH.max);
    await act(async () => fireEvent.keyDown(handle, { key: 'ArrowRight' }));
    expect(handle.getAttribute('aria-valuenow')).toBe(String(NOTEBOOK_SIDEBAR_WIDTH.max));

    await act(async () => fireEvent.keyDown(handle, { key: 'Home' }));
    expect(onPanelWidthChange).toHaveBeenLastCalledWith(NOTEBOOK_SIDEBAR_WIDTH.min);
  });

  it('reports the panel as closed when the remote unmounts while open', async () => {
    mockEmptyConversations();
    const { open, unmount, onPanelVisibilityChange } = await setup();
    await open();

    await unmount();
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(false);
  });
});
