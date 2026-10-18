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

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AssistantConversation } from '../model/assistantContract';
import { ConversationHeader } from './ConversationHeader';

const conversation = (title: string | undefined, canSendMessage = true): AssistantConversation => ({
  id: 'a',
  title,
  ownerId: 'analyst',
  canSendMessage,
  updatedAt: '2026-10-15T11:00:00.000Z'
});

const noop = () => {};

describe('ConversationHeader', () => {
  it('shows the selected conversation title', () => {
    render(
      <ConversationHeader conversation={conversation('Explain revenue by region')} onCreate={noop} onDelete={noop} />
    );
    expect(screen.getByText('Explain revenue by region')).toBeTruthy();
  });

  it('shows a placeholder when no conversation is selected', () => {
    render(<ConversationHeader onCreate={noop} onDelete={noop} />);
    expect(screen.getByText('No conversation selected')).toBeTruthy();
  });

  it('offers no delete button for a read-only conversation', () => {
    render(<ConversationHeader conversation={conversation('Shared', false)} onCreate={noop} onDelete={noop} />);
    expect(screen.queryByRole('button', { name: 'Delete Shared' })).toBeNull();
    expect(screen.getByText('Read-only')).toBeTruthy();
  });

  it('shows a fallback title for a missing or empty title', () => {
    const { rerender } = render(
      <ConversationHeader conversation={conversation(undefined)} onCreate={noop} onDelete={noop} />
    );
    expect(screen.getByText('Untitled conversation')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Delete Untitled conversation' })).toBeTruthy();
    rerender(<ConversationHeader conversation={conversation('')} onCreate={noop} onDelete={noop} />);
    expect(screen.getByText('Untitled conversation')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Delete Untitled conversation' })).toBeTruthy();
  });

  it('reports the deleted conversation id without asking for confirmation', () => {
    const onDelete = vi.fn();
    render(
      <ConversationHeader
        conversation={conversation('Explain revenue by region')}
        onCreate={noop}
        onDelete={onDelete}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete Explain revenue by region' }));
    expect(onDelete).toHaveBeenCalledWith('a');
  });

  it('reports a new-conversation request', () => {
    const onCreate = vi.fn();
    render(<ConversationHeader onCreate={onCreate} onDelete={noop} />);
    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));
    expect(onCreate).toHaveBeenCalled();
  });
});
