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

import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AssistantConversation } from '../model/assistantContract';
import { ConversationList } from './ConversationList';

const NOW = new Date(2026, 9, 15, 12).getTime();

const conversations: AssistantConversation[] = [
  {
    id: 'a',
    ownerId: 'user1',
    title: 'Explain revenue by region',
    updatedAt: new Date(2026, 9, 15, 11).toISOString(),
    canSendMessage: true
  },
  {
    id: 'b',
    ownerId: 'user1',
    title: 'Draft a query',
    updatedAt: new Date(2026, 9, 10, 9).toISOString(),
    canSendMessage: true
  },
  {
    id: 'c',
    ownerId: 'user1',
    title: 'Shared analysis',
    canSendMessage: false,
    updatedAt: new Date(2026, 9, 9, 9).toISOString()
  }
];

const noop = () => {};

describe('ConversationList', () => {
  it('shows an empty-list message when there are no conversations', () => {
    render(
      <ConversationList
        conversations={[]}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getByRole('status').textContent).toBe('No conversations yet.');
  });

  it('shows a different message for a search with no matches', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), {
      target: { value: 'nothing matches this' }
    });
    expect(screen.getByRole('status').textContent).toBe('No conversations match your search.');
  });

  it('filters by title, case-insensitively and trimmed', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), {
      target: { value: '  REVENUE  ' }
    });
    expect(screen.getByText('Explain revenue by region')).toBeTruthy();
    expect(screen.queryByText('Draft a query')).toBeNull();
  });

  it('reports the search term through the callback', () => {
    const onSearchTermChange = vi.fn();
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={onSearchTermChange}
        now={NOW}
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), { target: { value: 'query' } });
    expect(onSearchTermChange).toHaveBeenCalledWith('query');
  });

  it('marks the selected conversation', () => {
    render(
      <ConversationList
        conversations={conversations}
        selectedId="a"
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getByRole('button', { name: /^Explain revenue by region/ }).getAttribute('aria-current')).toBe(
      'true'
    );
  });

  it('reports the selected conversation id', () => {
    const onSelect = vi.fn();
    render(
      <ConversationList
        conversations={conversations}
        onSelect={onSelect}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: /^Explain revenue by region/ }));
    expect(onSelect).toHaveBeenCalledWith('a');
  });

  it('reports the deleted conversation id without asking for confirmation', () => {
    const onDelete = vi.fn();
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={onDelete}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete Explain revenue by region' }));
    expect(onDelete).toHaveBeenCalledWith('a');
  });

  it('offers no delete button for a read-only conversation', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.queryByRole('button', { name: 'Delete Shared analysis' })).toBeNull();
  });

  it('reports a new-conversation request', () => {
    const onCreate = vi.fn();
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={onCreate}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));
    expect(onCreate).toHaveBeenCalled();
  });

  it('splits today from earlier conversations, each under its own section', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getByText('Today')).toBeTruthy();
    expect(screen.getByText('Earlier')).toBeTruthy();
    const [todayList, earlierList] = screen.getAllByRole('list');
    expect(within(todayList).getByText('Explain revenue by region')).toBeTruthy();
    expect(within(earlierList).getByText('Draft a query')).toBeTruthy();
    expect(within(earlierList).getByText('Shared analysis')).toBeTruthy();
  });

  it('splits at local midnight', () => {
    render(
      <ConversationList
        conversations={[
          {
            id: 'x',
            ownerId: 'user1',
            title: 'After midnight',
            updatedAt: new Date(2026, 9, 15, 0, 1).toISOString(),
            canSendMessage: true
          },
          {
            id: 'y',
            ownerId: 'user1',
            title: 'Before midnight',
            updatedAt: new Date(2026, 9, 14, 23, 59).toISOString(),
            canSendMessage: true
          }
        ]}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    const [todayList, earlierList] = screen.getAllByRole('list');
    expect(within(todayList).getByText('After midnight')).toBeTruthy();
    expect(within(earlierList).getByText('Before midnight')).toBeTruthy();
  });

  it('hides a section heading when that section is empty', () => {
    render(
      <ConversationList
        conversations={[conversations[0]]}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getByText('Today')).toBeTruthy();
    expect(screen.queryByText('Earlier')).toBeNull();
  });

  it('shows a relative time for a today conversation and none for an earlier one', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getByText('1 hr. ago')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Draft a query' }).querySelector('time')).toBeNull();
  });

  it('drops section headings while searching', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), { target: { value: 'a' } });
    expect(screen.queryByText('Today')).toBeNull();
    expect(screen.queryByText('Earlier')).toBeNull();
  });

  it('does not match by date, only by title', () => {
    render(
      <ConversationList
        conversations={conversations}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), {
      target: { value: '2026-10-10' }
    });
    expect(screen.getByRole('status').textContent).toBe('No conversations match your search.');
  });
});
