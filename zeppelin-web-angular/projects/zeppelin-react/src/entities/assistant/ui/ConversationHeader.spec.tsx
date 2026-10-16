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

const noop = () => {};

describe('ConversationHeader', () => {
  it('shows the selected conversation title', () => {
    const conversation: AssistantConversation = { id: 'a', title: 'Explain revenue by region' };
    render(<ConversationHeader conversation={conversation} onCreate={noop} onDelete={noop} />);
    expect(screen.getByText('Explain revenue by region')).toBeTruthy();
  });

  it('shows a placeholder when no conversation is selected', () => {
    render(<ConversationHeader onCreate={noop} onDelete={noop} />);
    expect(screen.getByText('No conversation selected')).toBeTruthy();
  });

  it('offers no delete button for a read-only conversation', () => {
    const conversation: AssistantConversation = { id: 'a', title: 'Shared', canSendMessage: false };
    render(<ConversationHeader conversation={conversation} onCreate={noop} onDelete={noop} />);
    expect(screen.queryByRole('button', { name: 'Delete Shared' })).toBeNull();
  });

  it('reports the deleted conversation id without asking for confirmation', () => {
    const onDelete = vi.fn();
    const conversation: AssistantConversation = { id: 'a', title: 'Explain revenue by region' };
    render(<ConversationHeader conversation={conversation} onCreate={noop} onDelete={onDelete} />);
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
