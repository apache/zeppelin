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
import { fn } from 'storybook/test';
import type { AssistantConversation } from '../model/assistantContract';
import { ConversationHeader } from './ConversationHeader';

const current: AssistantConversation = { id: 'a', title: 'Explain how the notebook calculates revenue' };
const readOnly: AssistantConversation = { id: 'b', title: 'Compare regional totals', canSendMessage: false };

const meta = {
  title: 'Assistant/ConversationHeader',
  component: ConversationHeader,
  args: { conversation: current, onCreate: fn(), onDelete: fn() }
} satisfies Meta<typeof ConversationHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Current: Story = {};
export const ReadOnly: Story = { args: { conversation: readOnly } };
export const NoSelection: Story = { args: { conversation: undefined } };
