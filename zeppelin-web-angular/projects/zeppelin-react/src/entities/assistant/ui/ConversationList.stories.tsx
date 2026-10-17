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

import type { Meta, StoryObj } from '@storybook/react-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
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
    title: 'Draft a query for monthly orders',
    updatedAt: new Date(2026, 9, 10, 9).toISOString(),
    canSendMessage: true
  },
  {
    id: 'c',
    ownerId: 'user2',
    title: 'Review shared analysis',
    canSendMessage: false,
    updatedAt: new Date(2026, 9, 9, 9).toISOString()
  }
];

const meta = {
  title: 'Assistant/ConversationList',
  component: ConversationList,
  args: {
    conversations,
    onSelect: fn(),
    onCreate: fn(),
    onDelete: fn(),
    onSearchTermChange: fn(),
    now: NOW
  }
} satisfies Meta<typeof ConversationList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const TodayAndEarlier: Story = {};

export const Empty: Story = { args: { conversations: [] } };

export const Search: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.type(canvas.getByRole('textbox', { name: 'Search conversations' }), 'query');
    await expect(args.onSearchTermChange).toHaveBeenCalledWith('query');
    await expect(canvas.getByText('Draft a query for monthly orders')).toBeInTheDocument();
    await expect(canvas.queryByText('Explain revenue by region')).toBeNull();
  }
};

export const SelectAndDelete: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: /^Explain revenue by region/ }));
    await expect(args.onSelect).toHaveBeenCalledWith('a');
    await userEvent.click(canvas.getByRole('button', { name: 'Delete Explain revenue by region' }));
    await expect(args.onDelete).toHaveBeenCalledWith('a');
  }
};
