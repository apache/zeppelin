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
import { AssistantConnectionError, AssistantStreamError } from '../model/assistantContract';
import type { AssistantAnswerView } from './answerView';
import { AssistantAnswer } from './AssistantAnswer';

const partial = 'The notebook groups orders by region and';
const reply = 'The notebook groups orders by region and ranks them by total revenue.';

/** One fixture per state, in the order a run passes through them. */
const fixtures = {
  loading: { status: 'loading' },
  streaming: { status: 'streaming', content: partial },
  toolRunning: {
    status: 'streaming',
    content: '',
    tools: [
      { id: 't1', name: 'list_paragraphs', status: 'done' },
      { id: 't2', name: 'list_paragraphs', status: 'running' }
    ]
  },
  completed: { status: 'done', message: { id: 'a1', role: 'assistant', segments: [{ id: 's1', content: reply }] } },
  completedWithTools: {
    status: 'done',
    message: {
      id: 'a1',
      role: 'assistant',
      segments: [
        {
          id: 's1',
          content: '',
          toolCalls: [
            { id: 't1', name: 'list_paragraphs' },
            { id: 't2', name: 'list_paragraphs' }
          ]
        },
        { id: 's2', content: reply }
      ]
    }
  },
  failed: { status: 'error', kind: 'failed', message: 'The model request failed.' },
  streamError: {
    status: 'error',
    kind: 'stream',
    message: new AssistantStreamError().message,
    content: partial,
    tools: [{ id: 't1', name: 'list_paragraphs', status: 'done' }]
  },
  connectionLost: {
    status: 'error',
    kind: 'connection',
    message: new AssistantConnectionError().message,
    content: partial,
    tools: [{ id: 't1', name: 'read_results', status: 'running' }]
  }
} satisfies Record<string, AssistantAnswerView>;

type FixtureName = keyof typeof fixtures;

const meta = {
  title: 'Assistant/Status',
  component: AssistantAnswer,
  args: { view: fixtures.loading, onRetry: () => {} }
} satisfies Meta<typeof AssistantAnswer>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Loading: Story = {};
export const Streaming: Story = { args: { view: fixtures.streaming } };
export const ToolRunning: Story = { args: { view: fixtures.toolRunning } };
export const Completed: Story = { args: { view: fixtures.completed } };
export const CompletedWithTools: Story = { args: { view: fixtures.completedWithTools } };
export const Failed: Story = { args: { view: fixtures.failed } };
export const StreamError: Story = { args: { view: fixtures.streamError } };
export const ConnectionLost: Story = { args: { view: fixtures.connectionLost } };
export const ErrorWithoutRetry: Story = { args: { view: fixtures.failed, onRetry: undefined } };

/** Switch the fixture from the controls panel to check that each state fully replaces the last. */
export const StateSwitcher: StoryObj<{ state: FixtureName }> = {
  args: { state: 'loading' },
  argTypes: { state: { control: 'select', options: Object.keys(fixtures) } },
  render: ({ state }) => <AssistantAnswer view={fixtures[state]} onRetry={() => {}} />
};
