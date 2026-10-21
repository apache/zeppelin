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

import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AssistantConnectionError, type AssistantRunEvent } from '@/entities/assistant/model/assistantContract';
import { createAssistantFixture, type AssistantFixtureScenario } from '../fixtures/assistantFixture';
import { ASSISTANT_HISTORY_CONVERSATIONS, FIXTURE_NOW } from '../fixtures/assistantFixtureData';
import type { AssistantDataSource } from '../model/assistantDataSource';
import { AssistantConversationPanel } from './AssistantConversationPanel';

const flush = async () => {
  await act(async () => {});
};

const deferred = () => {
  let resolve!: () => void;
  const promise = new Promise<void>(accept => {
    resolve = accept;
  });

  return { promise, resolve };
};

const input = () => screen.getByRole('textbox', { name: 'Question' }) as HTMLTextAreaElement;
const send = () => screen.getByRole('button', { name: 'Send' }) as HTMLButtonElement;
const listTrigger = () => screen.getByRole('button', { name: 'Open conversations' });
const messages = () => screen.getByRole('region', { name: 'Conversation messages' });

const mountPanel = async (source: AssistantDataSource, onKeyDown = vi.fn()) => {
  render(
    <div onKeyDown={onKeyDown}>
      <AssistantConversationPanel source={source} now={FIXTURE_NOW} />
    </div>
  );
  await flush();
  return { onKeyDown };
};

const select = async (title: string) => {
  fireEvent.click(listTrigger());
  fireEvent.click(screen.getByRole('button', { name: new RegExp(`^${title}`) }));
  await flush();
};

const submit = async (question: string) => {
  fireEvent.change(input(), { target: { value: question } });
  fireEvent.click(send());
  await flush();
};

const fixture = (scenario?: AssistantFixtureScenario) =>
  createAssistantFixture({ conversations: ASSISTANT_HISTORY_CONVERSATIONS, now: FIXTURE_NOW, scenario, delayMs: 0 });

const streamingFixture = () => {
  const source = fixture();
  const firstDelta = deferred();
  const secondDelta = deferred();
  const finish = deferred();
  const run = vi.fn(async function* (): AsyncIterable<AssistantRunEvent> {
    yield { type: 'run.started' };
    await firstDelta.promise;
    yield { type: 'message.delta', messageId: 'controlled-answer', delta: 'Only revenue A' };
    await secondDelta.promise;
    yield { type: 'message.delta', messageId: 'controlled-answer', delta: ' continues' };
    await finish.promise;
    yield { type: 'run.failed', message: 'Controlled run stopped.' };
  });

  return { source: { ...source, run }, run, firstDelta, secondDelta, finish };
};

describe('AssistantConversationPanel', () => {
  it('opens saved conversations and restores the trigger after select, Back and Escape without closing the shell', async () => {
    const source = fixture();
    const getMessages = vi.spyOn(source.api, 'getMessages');
    const { onKeyDown } = await mountPanel(source);

    await select('Revenue analysis');

    expect(screen.getByText('Revenue increased in all three regions, led by Europe.')).toBeTruthy();
    expect(document.activeElement).toBe(listTrigger());

    fireEvent.change(input(), { target: { value: 'Keep my selected draft' } });

    fireEvent.click(listTrigger());

    expect(screen.getByRole('button', { name: /^Revenue analysis/ }).getAttribute('aria-current')).toBe('true');

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));

    expect(input().value).toBe('Keep my selected draft');
    expect(document.activeElement).toBe(listTrigger());

    fireEvent.click(listTrigger());
    const search = screen.getByRole('textbox', { name: 'Search conversations' });
    search.focus();
    fireEvent.keyDown(search, { key: 'Escape' });

    expect(screen.queryByRole('textbox', { name: 'Search conversations' })).toBeNull();
    expect(input().value).toBe('Keep my selected draft');
    expect(document.activeElement).toBe(listTrigger());
    expect(onKeyDown).not.toHaveBeenCalled();
    expect(getMessages.mock.calls).toEqual([['c1']]);

    fireEvent.keyDown(input(), { key: 'ArrowLeft' });

    expect(onKeyDown).toHaveBeenCalledTimes(1);
  });

  it('renders a saved answer whose opaque id matches an object property', async () => {
    const source = fixture();
    vi.spyOn(source.api, 'getMessages').mockResolvedValueOnce({
      messages: [
        {
          id: 'constructor',
          role: 'assistant',
          segments: [{ id: 'saved-segment', content: 'This saved answer must remain visible.' }]
        }
      ],
      cursor: null
    });
    await mountPanel(source);

    await select('Revenue analysis');

    expect(within(messages()).getByText('This saved answer must remain visible.')).toBeTruthy();
  });

  it('loads earlier history after a rejected send supersedes its pending page request', async () => {
    // Make the viewport eligible for automatic history loading.
    const height = vi.spyOn(Element.prototype, 'clientHeight', 'get').mockReturnValue(100);
    const width = vi.spyOn(Element.prototype, 'clientWidth', 'get').mockReturnValue(100);
    const earlier = deferred();

    try {
      const source = fixture('send-rejection');
      const getMessages = source.api.getMessages;
      let pending = true;
      vi.spyOn(source.api, 'getMessages').mockImplementation(async (id, before) => {
        if (before && pending) {
          pending = false;
          await earlier.promise;
        }
        return getMessages(id, before);
      });
      await mountPanel(source);

      await select('Revenue analysis');

      expect(source.api.getMessages).toHaveBeenCalledWith('c1', 'c1:earlier');

      await submit('A question rejected before acceptance');
      await act(async () => earlier.resolve());

      expect(await within(messages()).findByText('I checked the regional revenue paragraph.')).toBeTruthy();
      expect(input().value).toBe('A question rejected before acceptance');
    } finally {
      earlier.resolve();
      height.mockRestore();
      width.mockRestore();
    }
  });

  it('keeps initial and repeated New local, preserving its draft until the first send', async () => {
    const source = fixture();
    const create = vi.spyOn(source.api, 'createConversation');
    const run = vi.spyOn(source, 'run');
    await mountPanel(source);

    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));
    fireEvent.change(input(), { target: { value: 'Local draft question' } });
    await select('Notebook summary');
    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));

    expect(input().value).toBe('Local draft question');

    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));

    expect(create).not.toHaveBeenCalled();
    expect(run).not.toHaveBeenCalled();

    fireEvent.click(send());
    await flush();

    expect(create).toHaveBeenCalledTimes(1);
    expect(run).toHaveBeenCalledWith('c4', 'Local draft question', expect.any(AbortSignal));
    expect(screen.getAllByRole('article', { name: 'Your question' })).toHaveLength(1);
    expect(
      within(screen.getByRole('article', { name: 'Your question' })).getByText('Local draft question')
    ).toBeTruthy();
    expect(screen.getAllByRole('article', { name: 'Assistant answer' })).toHaveLength(1);
    expect(screen.getByRole('table')).toBeTruthy();
    expect(screen.getByText('12%', { selector: 'strong' })).toBeTruthy();
  });

  it('disables only New during creation, including the list, and enables it before the accepted run finishes', async () => {
    const source = fixture();
    const creation = deferred();
    const acceptance = deferred();
    const create = source.api.createConversation;
    source.api.createConversation = async body => {
      await creation.promise;
      return create(body);
    };
    source.run = async function* () {
      await acceptance.promise;
      yield { type: 'run.started' };
      yield { type: 'run.failed', message: 'Controlled completion.' };
    };
    await mountPanel(source);

    await submit('Create this draft');

    expect(screen.getByRole('button', { name: 'New conversation' }).matches(':disabled')).toBe(true);

    await select('Notebook summary');

    expect(input().disabled).toBe(false);

    fireEvent.change(input(), { target: { value: 'Existing conversation remains editable' } });

    expect(send().matches(':disabled')).toBe(false);
    expect(screen.getByRole('button', { name: 'New conversation' }).matches(':disabled')).toBe(true);

    fireEvent.click(listTrigger());

    expect(screen.getByRole('button', { name: 'New conversation' }).matches(':disabled')).toBe(true);

    await act(async () => creation.resolve());

    expect(screen.getByRole('button', { name: 'New conversation' }).matches(':disabled')).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));

    expect(input().value).toBe('Existing conversation remains editable');

    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));

    expect(input().value).toBe('');

    await act(async () => acceptance.resolve());
  });

  it('preserves question selection and expanded tool focus through completion and saved history', async () => {
    const source = fixture();
    const completion = deferred();
    const history = deferred();
    const originalRun = source.run;
    const originalGetMessages = source.api.getMessages;
    source.run = async function* (id, content, signal) {
      for await (const event of originalRun(id, content, signal)) {
        if (event.type === 'run.completed') await completion.promise;
        yield event;
      }
    };
    vi.spyOn(source.api, 'getMessages').mockImplementationOnce(async id => {
      await history.promise;
      return originalGetMessages(id);
    });
    await mountPanel(source);

    await submit('Keep this question selected');

    const summary = screen.getByText('Used 1 tool');
    summary.closest('details')!.open = true;
    summary.focus();
    const selection = window.getSelection()!;
    const range = document.createRange();
    range.selectNodeContents(screen.getByText('Keep this question selected'));
    selection.removeAllRanges();
    selection.addRange(range);

    await act(async () => completion.resolve());

    expect(within(messages()).queryByText('Loading conversation…')).toBeNull();
    expect(screen.getByText('Used 1 tool').closest('details')!.open).toBe(true);
    expect(document.activeElement).toBe(summary);
    expect(selection.toString()).toBe('Keep this question selected');

    await act(async () => history.resolve());

    expect(screen.getByText('Used 1 tool').closest('details')!.open).toBe(true);
    expect(document.activeElement).toBe(summary);
    expect(selection.toString()).toBe('Keep this question selected');
    expect(
      within(messages())
        .getAllByRole('article')
        .map(article => article.getAttribute('aria-label'))
    ).toEqual(['Your question', 'Assistant answer']);

    selection.removeAllRanges();
  });

  it('keeps an earlier identical question selected when another send overtakes its history refresh', async () => {
    const source = fixture();
    const history = deferred();
    const originalGetMessages = source.api.getMessages;
    vi.spyOn(source.api, 'getMessages').mockImplementationOnce(async id => {
      await history.promise;
      return originalGetMessages(id);
    });
    await mountPanel(source);
    await submit('Repeated question');

    const summary = screen.getByText('Used 1 tool');
    summary.closest('details')!.open = true;
    const selection = window.getSelection()!;
    const range = document.createRange();
    range.selectNodeContents(screen.getByText('Repeated question'));
    selection.removeAllRanges();
    selection.addRange(range);

    try {
      await submit('Repeated question');

      expect(within(messages()).getAllByText('Repeated question')).toHaveLength(2);
      expect(selection.toString()).toBe('Repeated question');
      expect(within(messages()).getAllByText('Used 1 tool')[0].closest('details')!.open).toBe(true);

      await act(async () => history.resolve());

      expect(within(messages()).getAllByText('Repeated question')).toHaveLength(2);
      expect(selection.toString()).toBe('Repeated question');
    } finally {
      await act(async () => history.resolve());
      selection.removeAllRanges();
    }
  });

  it('cancels deletion without calling the API, and removes the real fixture conversation only after confirmation', async () => {
    const source = fixture();
    const remove = vi.spyOn(source.api, 'deleteConversation');
    await mountPanel(source);
    await select('Revenue analysis');

    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));

    const dialog = screen.getByRole('dialog', { name: 'Delete conversation' });
    expect(remove).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(remove).not.toHaveBeenCalled();
    expect(screen.getByText('Revenue increased in all three regions, led by Europe.')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));
    await flush();

    expect(remove).toHaveBeenCalledExactlyOnceWith('c1');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.queryByText('Revenue increased in all three regions, led by Europe.')).toBeNull();

    fireEvent.click(listTrigger());

    expect(screen.queryByRole('button', { name: /^Revenue analysis/ })).toBeNull();
    expect((await source.api.listConversations()).map(item => item.id)).toEqual(['c2', 'c3']);
  });

  it('consumes modal Escape without deleting or bubbling to the shell', async () => {
    const source = fixture();
    const remove = vi.spyOn(source.api, 'deleteConversation');
    const { onKeyDown } = await mountPanel(source);
    await select('Revenue analysis');

    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));

    const cancel = within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' });
    expect(document.activeElement).toBe(cancel);

    fireEvent.keyDown(cancel, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(remove).not.toHaveBeenCalled();
    expect(onKeyDown).not.toHaveBeenCalled();
    expect(screen.getByText('Revenue increased in all three regions, led by Europe.')).toBeTruthy();
  });

  it('shows a delete rejection and retains the conversation rather than pretending it was removed', async () => {
    const source = fixture();
    const remove = vi
      .spyOn(source.api, 'deleteConversation')
      .mockRejectedValueOnce(new Error('Cannot delete this conversation.'));
    await mountPanel(source);

    fireEvent.click(listTrigger());
    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));
    await flush();

    expect(remove).toHaveBeenCalledExactlyOnceWith('c1');
    expect(
      screen.getAllByRole('alert').some(alert => alert.textContent?.includes('Cannot delete this conversation.'))
    ).toBe(true);
    expect(screen.getByRole('button', { name: /^Revenue analysis/ })).toBeTruthy();
    expect((await source.api.listConversations()).some(item => item.id === 'c1')).toBe(true);
  });

  it('shows create failure, retains the draft, and retries creation only on an explicit send', async () => {
    const source = fixture('create-failure');
    const create = vi.spyOn(source.api, 'createConversation');
    const run = vi.spyOn(source, 'run');
    await mountPanel(source);

    await submit('Retain this question');

    expect(screen.getByRole('alert').textContent).toContain('could not create the conversation');
    expect(input().value).toBe('Retain this question');
    expect(create).toHaveBeenCalledTimes(1);
    expect(run).not.toHaveBeenCalled();
    expect(send().matches(':disabled')).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await flush();

    expect(create).toHaveBeenCalledTimes(2);
    expect(run).toHaveBeenCalledExactlyOnceWith('c4', 'Retain this question', expect.any(AbortSignal));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('retries a rejected first send using its confirmed conversation id without creating another conversation', async () => {
    const source = fixture('send-rejection');
    const create = vi.spyOn(source.api, 'createConversation');
    const run = vi.spyOn(source, 'run');
    await mountPanel(source);

    await submit('Retry the same conversation');

    expect(screen.getByRole('alert').textContent).toContain('could not accept this message');
    expect(input().value).toBe('Retry the same conversation');
    expect(send().matches(':disabled')).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await flush();

    expect(create).toHaveBeenCalledTimes(1);
    expect(run.mock.calls.map(([id]) => id)).toEqual(['c4', 'c4']);
    expect(screen.getAllByRole('article', { name: 'Your question' })).toHaveLength(1);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('keeps an unknown draft editable and reloads only on explicit recovery without automatically resending', async () => {
    const source = fixture('unknown');
    const run = vi.spyOn(source, 'run');
    const getMessages = vi.spyOn(source.api, 'getMessages');
    await mountPanel(source);

    await submit('Was this accepted?');

    expect(input().value).toBe('Was this accepted?');
    expect(input().disabled).toBe(false);

    fireEvent.change(input(), { target: { value: 'My next explicit question' } });

    expect(input().value).toBe('My next explicit question');
    expect(send().matches(':disabled')).toBe(false);
    expect(run).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Reload conversation' }));
    await flush();

    expect(getMessages).toHaveBeenCalledExactlyOnceWith('c4');
    expect(screen.getByRole('article', { name: 'Your question' }).textContent).toContain('Was this accepted?');
    expect(input().value).toBe('My next explicit question');
    expect(send().matches(':disabled')).toBe(false);
    expect(run).toHaveBeenCalledTimes(1);

    fireEvent.click(send());
    await flush();

    expect(run).toHaveBeenCalledTimes(2);
    expect(run.mock.calls[1][0]).toBe('c4');
    expect(run.mock.calls[1][1]).toBe('My next explicit question');
  });

  it('allows an explicit normal Send immediately after an unknown outcome without a history prerequisite', async () => {
    const source = fixture('unknown');
    const run = vi.spyOn(source, 'run');
    const create = vi.spyOn(source.api, 'createConversation');
    const getMessages = vi.spyOn(source.api, 'getMessages');
    await mountPanel(source);

    await submit('First question with unknown acceptance');

    expect(run).toHaveBeenCalledTimes(1);
    expect(getMessages).not.toHaveBeenCalled();

    fireEvent.change(input(), { target: { value: 'Explicit next question' } });

    expect(send().matches(':disabled')).toBe(false);

    fireEvent.click(send());
    await flush();

    expect(create).toHaveBeenCalledTimes(1);
    expect(run.mock.calls.map(([id, question]) => [id, question])).toEqual([
      ['c4', 'First question with unknown acceptance'],
      ['c4', 'Explicit next question']
    ]);
    expect(screen.getByRole('table')).toBeTruthy();
  });

  it('offers the existing explicit Retry after an accepted failure without requiring a history reload', async () => {
    const source = fixture('run-failure');
    const run = vi.spyOn(source, 'run');
    const getMessages = vi.spyOn(source.api, 'getMessages');
    await mountPanel(source);

    await submit('Read the revenue data');

    expect(screen.getAllByRole('alert').some(alert => alert.textContent?.includes('stopped while reading'))).toBe(true);
    expect(input().value).toBe('Read the revenue data');
    expect(send().matches(':disabled')).toBe(false);
    expect(run).toHaveBeenCalledTimes(1);
    expect(getMessages).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await flush();

    expect(run).toHaveBeenCalledTimes(2);
    expect(run.mock.calls.map(([id]) => id)).toEqual(['c4', 'c4']);
  });

  it('keeps a late A stream out of B and returns to A without starting a second consumer', async () => {
    const harness = streamingFixture();
    const getMessages = vi.spyOn(harness.source.api, 'getMessages');
    await mountPanel(harness.source);
    await select('Revenue analysis');
    await submit('Question for A');
    await select('Notebook summary');
    fireEvent.change(input(), { target: { value: 'Question for B' } });
    input().focus();
    const focusedComposer = input();

    await act(async () => harness.firstDelta.resolve());

    expect(screen.queryByText('Only revenue A')).toBeNull();
    expect(screen.getByText('This notebook compares monthly revenue across regions.')).toBeTruthy();
    expect(input().value).toBe('Question for B');
    expect(document.activeElement).toBe(focusedComposer);

    const loads = getMessages.mock.calls.length;
    await select('Revenue analysis');

    expect(screen.getByText('Only revenue A')).toBeTruthy();
    expect(harness.run).toHaveBeenCalledTimes(1);
    expect(getMessages).toHaveBeenCalledTimes(loads);

    await act(async () => {
      harness.secondDelta.resolve();
      harness.finish.resolve();
    });
  });

  it('disables the composer for read-only history without interpreting it as an active run', async () => {
    const source = fixture();
    const run = vi.spyOn(source, 'run');
    await mountPanel(source);

    await select('Shared analysis');

    expect(screen.getByText('This shared conversation can be read, but not changed.')).toBeTruthy();
    expect(input().disabled).toBe(true);
    expect(send().matches(':disabled')).toBe(true);
    expect(screen.queryByRole('button', { name: 'Delete Shared analysis' })).toBeNull();
    expect(screen.queryByText('Thinking…')).toBeNull();

    fireEvent.submit(input().form!);

    expect(run).not.toHaveBeenCalled();
  });

  it('shows a history failure and reloads explicitly without sending a question', async () => {
    const source = fixture();
    const getMessages = vi
      .spyOn(source.api, 'getMessages')
      .mockRejectedValueOnce(new Error('Saved history is unavailable.'));
    const run = vi.spyOn(source, 'run');
    await mountPanel(source);

    await select('Revenue analysis');

    expect(screen.getByRole('alert').textContent).toContain('Saved history is unavailable.');

    fireEvent.click(screen.getByRole('button', { name: 'Reload conversation' }));
    await flush();

    expect(getMessages.mock.calls).toEqual([['c1'], ['c1']]);
    expect(screen.getByText('Revenue increased in all three regions, led by Europe.')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(run).not.toHaveBeenCalled();
  });

  it('confirms deletion of an active conversation and lets the API decide the result', async () => {
    const harness = streamingFixture();
    const remove = vi
      .spyOn(harness.source.api, 'deleteConversation')
      .mockRejectedValueOnce(new Error('Question active.'));
    await mountPanel(harness.source);
    await select('Revenue analysis');
    await submit('Keep A running');
    await select('Notebook summary');

    fireEvent.click(listTrigger());
    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));

    expect(screen.getByRole('dialog')).toBeTruthy();
    expect(remove).not.toHaveBeenCalled();

    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));
    await flush();

    expect(remove).toHaveBeenCalledExactlyOnceWith('c1');
    expect(within(screen.getByRole('dialog')).getByRole('alert').textContent).toContain('Question active.');

    await act(async () => {
      harness.firstDelta.resolve();
      harness.secondDelta.resolve();
      harness.finish.resolve();
    });
  });

  it('discards a confirmation when the source changes rather than applying it to the same id in another source', async () => {
    const first = fixture();
    const second = fixture();
    const remove = vi.spyOn(second.api, 'deleteConversation');
    const view = render(<AssistantConversationPanel source={first} now={FIXTURE_NOW} />);
    await flush();
    await select('Revenue analysis');

    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));

    expect(screen.getByRole('dialog')).toBeTruthy();

    view.rerender(<AssistantConversationPanel source={second} now={FIXTURE_NOW} />);
    await flush();

    expect(screen.queryByRole('dialog')).toBeNull();

    fireEvent.click(listTrigger());

    expect(screen.getByRole('button', { name: /^Revenue analysis/ })).toBeTruthy();
    expect(remove).not.toHaveBeenCalled();
  });

  it('ignores an old deletion continuation without clearing a new source confirmation or its cancel action', async () => {
    const first = fixture();
    const second = fixture();
    const gate = deferred();
    const remove = vi.spyOn(second.api, 'deleteConversation');
    const view = render(<AssistantConversationPanel source={first} now={FIXTURE_NOW} />);
    await flush();

    await submit('Temporary conversation');
    fireEvent.click(screen.getByRole('button', { name: 'Delete New conversation' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));
    await flush();

    await select('Notebook summary');
    fireEvent.click(screen.getByRole('button', { name: 'Delete Notebook summary' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));
    await flush();

    vi.spyOn(first.api, 'deleteConversation').mockImplementationOnce(() => gate.promise);
    await select('Revenue analysis');
    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));

    view.rerender(<AssistantConversationPanel source={second} now={FIXTURE_NOW} />);
    await flush();
    await select('Revenue analysis');
    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));

    await act(async () => gate.resolve());

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).queryByRole('alert')).toBeNull();
    const cancel = within(dialog).getByRole('button', { name: 'Cancel' });
    expect(cancel.matches(':disabled')).toBe(false);

    fireEvent.click(cancel);

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(remove).not.toHaveBeenCalled();
    expect(screen.getByText('Revenue increased in all three regions, led by Europe.')).toBeTruthy();
  });

  it('keeps the draft editable and normal Send available while history is loading', async () => {
    const source = fixture();
    const history = deferred();
    const originalGetMessages = source.api.getMessages;
    vi.spyOn(source.api, 'getMessages').mockImplementationOnce(async id => {
      await history.promise;
      return originalGetMessages(id);
    });
    const run = vi.spyOn(source, 'run');
    await mountPanel(source);

    await select('Revenue analysis');

    expect(screen.getByText('Loading conversation…')).toBeTruthy();
    expect(input().disabled).toBe(false);

    fireEvent.change(input(), { target: { value: 'Question during history lookup' } });

    expect(input().value).toBe('Question during history lookup');
    expect(send().matches(':disabled')).toBe(false);
    expect(run).not.toHaveBeenCalled();

    fireEvent.click(send());
    await flush();

    expect(run).toHaveBeenCalledExactlyOnceWith('c1', 'Question during history lookup', expect.any(AbortSignal));

    await act(async () => history.resolve());
  });

  it.each(['Cancel', 'Escape'])(
    'dismisses a pending delete with %s without cancelling the API or changing a later confirmation',
    async dismissal => {
      const source = fixture();
      const gate = deferred();
      const originalDelete = source.api.deleteConversation;
      const remove = vi.spyOn(source.api, 'deleteConversation').mockImplementationOnce(async id => {
        await gate.promise;
        return originalDelete(id);
      });
      await mountPanel(source);
      await select('Revenue analysis');

      fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));
      const confirm = within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' });
      fireEvent.click(confirm);
      fireEvent.click(confirm);

      expect(remove).toHaveBeenCalledExactlyOnceWith('c1');

      const cancel = within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' });
      if (dismissal === 'Escape') fireEvent.keyDown(cancel, { key: 'Escape' });
      else fireEvent.click(cancel);

      expect(screen.queryByRole('dialog')).toBeNull();

      await select('Notebook summary');
      fireEvent.click(screen.getByRole('button', { name: 'Delete Notebook summary' }));

      await act(async () => gate.resolve());

      const nextDialog = screen.getByRole('dialog');
      expect(within(nextDialog).getByText(/Delete “Notebook summary”/)).toBeTruthy();
      expect(within(nextDialog).queryByRole('alert')).toBeNull();
      expect(within(nextDialog).getByRole('button', { name: 'Delete conversation' }).matches(':disabled')).toBe(false);

      fireEvent.click(within(nextDialog).getByRole('button', { name: 'Delete conversation' }));
      await flush();

      expect(remove.mock.calls).toEqual([['c1'], ['c2']]);
      expect(screen.queryByRole('dialog')).toBeNull();

      fireEvent.click(listTrigger());

      expect(screen.queryByRole('button', { name: /^Revenue analysis/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /^Notebook summary/ })).toBeNull();
    }
  );

  it('closes a reopened confirmation when its pending deletion succeeds for the same conversation', async () => {
    const source = fixture();
    const gate = deferred();
    const originalDelete = source.api.deleteConversation;
    const remove = vi.spyOn(source.api, 'deleteConversation').mockImplementationOnce(async id => {
      await gate.promise;
      return originalDelete(id);
    });
    await mountPanel(source);
    await select('Revenue analysis');

    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete conversation' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Revenue analysis' }));

    expect(screen.getByRole('dialog')).toBeTruthy();

    await act(async () => gate.resolve());

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(remove).toHaveBeenCalledExactlyOnceWith('c1');

    fireEvent.click(listTrigger());

    expect(screen.queryByRole('button', { name: /^Revenue analysis/ })).toBeNull();
  });

  it.each(['failed', 'connection'])(
    'retains the previous %s answer outcome when the next question is rejected',
    async outcome => {
      const source = fixture();
      let first = true;
      const failure = outcome === 'failed' ? 'The first run failed.' : new AssistantConnectionError().message;
      source.run = async function* (): AsyncIterable<AssistantRunEvent> {
        if (!first) {
          yield { type: 'run.failed', message: 'The next question was rejected.' };
          return;
        }
        first = false;
        yield { type: 'run.started' };
        yield { type: 'message.delta', messageId: 'partial-answer', delta: 'Partial answer' };
        yield { type: 'tool_call.started', toolCallId: 'unfinished-tool', name: 'read_notebook' };
        if (outcome === 'connection') throw new AssistantConnectionError();
        yield { type: 'run.failed', message: failure };
      };
      await mountPanel(source);
      await select('Revenue analysis');
      await submit('First question');

      await submit('Next question');

      const retained = screen.getByText('Partial answer').closest('article')!;
      expect(within(retained).getByText(/status unknown/)).toBeTruthy();
      expect(within(retained).getByRole('alert').textContent).toContain(failure);
      expect(screen.getByText('The next question was rejected.')).toBeTruthy();
    }
  );
});
