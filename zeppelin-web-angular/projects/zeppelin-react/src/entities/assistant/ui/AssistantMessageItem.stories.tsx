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
import { AssistantAnswer } from './AssistantAnswer';
import { AssistantMessageItem } from './AssistantMessageItem';

const question = 'Explain how this notebook calculates revenue.';
const reply = 'The notebook groups orders by region and ranks them by total revenue.';

const meta = {
  title: 'Assistant/Message',
  component: AssistantMessageItem,
  args: { role: 'user', content: question }
} satisfies Meta<typeof AssistantMessageItem>;

export default meta;
type Story = StoryObj<typeof meta>;

export const User: Story = {};

export const CompletedReply: Story = {
  args: { role: 'assistant', content: reply }
};

export const StreamingReply: Story = {
  args: { role: 'assistant', content: 'The notebook groups orders by region and', streaming: true }
};

export const LongContent: Story = {
  args: {
    role: 'assistant',
    content: [
      'Paragraph 2 loads `orders` and joins it with `regions` on region_id.',
      'Paragraph 3 sums amount per region and sorts the totals in descending order.',
      'A very long identifier such as orders_by_region_with_currency_normalisation_applied_v2 wraps instead of overflowing.'
    ].join('\n\n')
  }
};

export const Conversation: Story = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <AssistantMessageItem role="user" content={question} />
      <AssistantAnswer
        view={{
          status: 'done',
          message: {
            id: 'a1',
            role: 'assistant',
            segments: [{ id: 's1', content: reply, toolCalls: [{ id: 't1', name: 'list_paragraphs' }] }]
          }
        }}
      />
    </div>
  )
};
