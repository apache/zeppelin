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

const at = (day: number, hour: number, minute = 0, second = 0) =>
  new Date(2026, 9, day, hour, minute, second).toISOString();

const conversation = (id: string, title: string | undefined, updatedAt: string, canSendMessage = true) => ({
  id,
  title,
  ownerId: 'analyst',
  canSendMessage,
  updatedAt
});

const conversations: AssistantConversation[] = [
  conversation('a', 'Explain revenue by region', at(15, 11)),
  conversation('b', 'Draft a query', at(10, 9)),
  conversation('c', 'Shared analysis', at(9, 9), false)
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

  it('shows the no-match message for an empty list passed while searching', () => {
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
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), { target: { value: 'query' } });
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
    fireEvent.click(screen.getByRole('button', { name: 'Draft a query' }));
    expect(onSelect).toHaveBeenCalledWith('b');
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
    fireEvent.click(screen.getByRole('button', { name: 'Delete Draft a query' }));
    expect(onDelete).toHaveBeenCalledWith('b');
  });

  it('shows a fallback title for a missing or empty title', () => {
    render(
      <ConversationList
        conversations={[conversation('x', undefined, at(10, 9)), conversation('y', '', at(10, 9))]}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getAllByRole('button', { name: 'Untitled conversation' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Delete Untitled conversation' })).toHaveLength(2);
  });

  it('matches an untitled conversation by its fallback title', () => {
    render(
      <ConversationList
        conversations={[...conversations, conversation('x', '', at(10, 9))]}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Search conversations' }), {
      target: { value: 'untitled' }
    });
    expect(screen.getByRole('button', { name: 'Untitled conversation' })).toBeTruthy();
    expect(screen.queryByText('Draft a query')).toBeNull();
  });

  it('marks a read-only conversation and no other', () => {
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
    expect(within(screen.getByRole('button', { name: /^Shared analysis/ })).getByText('Read-only')).toBeTruthy();
    expect(screen.getAllByText('Read-only')).toHaveLength(1);
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
          conversation('x', 'After midnight', at(15, 0, 1)),
          conversation('y', 'Before midnight', at(14, 23, 59))
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

  it('formats a relative time as now, minutes, or hours', () => {
    render(
      <ConversationList
        conversations={[
          conversation('x', 'Seconds ago', at(15, 11, 59, 40)),
          conversation('y', 'Minutes ago', at(15, 11, 55)),
          conversation('z', 'Hours ago', at(15, 9))
        ]}
        onSelect={noop}
        onCreate={noop}
        onDelete={noop}
        onSearchTermChange={noop}
        now={NOW}
      />
    );
    expect(screen.getByText('now')).toBeTruthy();
    expect(screen.getByText('5 min. ago')).toBeTruthy();
    expect(screen.getByText('3 hr. ago')).toBeTruthy();
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
