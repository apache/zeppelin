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

import { useMemo } from 'react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { expect, userEvent, waitFor, within } from 'storybook/test';
import { createAssistantFixture, type AssistantFixtureScenario } from '../fixtures/assistantFixture';
import {
  ASSISTANT_DEMO_CONVERSATIONS,
  ASSISTANT_HISTORY_CONVERSATIONS,
  FIXTURE_NOW
} from '../fixtures/assistantFixtureData';
import { AssistantConversationPanel } from './AssistantConversationPanel';

type PanelDemonstration =
  | 'ask'
  | 'completed-answer'
  | 'two-page-history'
  | 'conversation-switching'
  | 'creation-failure'
  | 'send-rejected'
  | 'acceptance-unknown'
  | 'accepted-run-failure';

const failureScenarios: Partial<Record<PanelDemonstration, AssistantFixtureScenario>> = {
  'creation-failure': 'create-failure',
  'send-rejected': 'send-rejection',
  'acceptance-unknown': 'unknown',
  'accepted-run-failure': 'run-failure'
};

const FixturePanel = ({ demonstration }: { demonstration: PanelDemonstration }) => {
  const source = useMemo(
    () =>
      createAssistantFixture({
        conversations:
          demonstration === 'two-page-history' ? ASSISTANT_HISTORY_CONVERSATIONS : ASSISTANT_DEMO_CONVERSATIONS,
        now: FIXTURE_NOW,
        scenario: failureScenarios[demonstration] ?? 'normal',
        delayMs: demonstration === 'conversation-switching' ? 500 : 120
      }),
    [demonstration]
  );

  return (
    <div style={{ width: 370, maxWidth: '100%', height: 600, display: 'flex', minWidth: 0 }}>
      <AssistantConversationPanel source={source} now={FIXTURE_NOW} />
    </div>
  );
};

const meta = {
  title: 'Assistant/ConversationPanel',
  component: FixturePanel,
  args: { demonstration: 'ask' },
  argTypes: { demonstration: { control: false, table: { disable: true } } }
} satisfies Meta<typeof FixturePanel>;
export default meta;
type Story = StoryObj<typeof meta>;

const ask = async (canvasElement: HTMLElement, question: string) => {
  const canvas = within(canvasElement);

  await userEvent.type(canvas.getByRole('textbox', { name: 'Question' }), question);
  await userEvent.click(canvas.getByRole('button', { name: 'Send' }));

  return canvas;
};

const selectConversation = async (canvasElement: HTMLElement, title: RegExp) => {
  const canvas = within(canvasElement);

  await userEvent.click(canvas.getByRole('button', { name: 'Open conversations' }));
  await userEvent.click(await canvas.findByRole('button', { name: title }));
};

export const AskInThePanel: Story = {
  parameters: {
    docs: {
      description: {
        story:
          'Manual interactive demo with compact saved conversations. Type a question and Send to create a conversation and stream an answer. New stays local until the first send.'
      }
    }
  }
};

export const CompletedAnswer: Story = {
  args: { demonstration: 'completed-answer' },
  parameters: {
    docs: {
      description: {
        story: 'Automatically asks a question and waits for the completed Markdown answer and table.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = await ask(canvasElement, 'Explain the revenue results.');
    const table = await canvas.findByRole('table', {}, { timeout: 10000 });
    const answer = table.closest('article')!;
    await waitFor(() => expect(answer).not.toHaveAttribute('aria-busy', 'true'), { timeout: 10000 });
    await expect(table).toBeVisible();
  }
};

export const TwoPageHistory: Story = {
  args: { demonstration: 'two-page-history' },
  parameters: {
    docs: {
      description: {
        story:
          'Automatically opens Revenue analysis and scrolls its message viewport to the top to load the older page. The earlier question and both parts of the boundary-split answer are then shown together.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await selectConversation(canvasElement, /^Revenue analysis/);
    await canvas.findByText('Revenue increased in all three regions, led by Europe.');

    const viewport = canvas.getByRole('region', { name: 'Conversation messages' });
    await waitFor(() => expect(viewport).not.toHaveAttribute('aria-busy', 'true'));
    viewport.scrollTo({ top: 0 });
    await expect(await canvas.findByText('How did revenue change by region?', {}, { timeout: 10000 })).toBeVisible();

    const earlierPart = await canvas.findByText('I checked the regional revenue paragraph.');
    const joinedAnswer = earlierPart.closest('article')!;
    await expect(earlierPart).toBeVisible();
    await expect(
      within(joinedAnswer).getByText('Revenue increased in all three regions, led by Europe.')
    ).toBeVisible();
  }
};

export const ConversationSwitching: Story = {
  args: { demonstration: 'conversation-switching' },
  parameters: {
    docs: {
      description: {
        story:
          'Sends in Revenue analysis, switches to Notebook summary during the answer, then returns to the completed result.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await selectConversation(canvasElement, /^Revenue analysis/);
    await canvas.findByText('Compare Europe’s order volume with its average order value.');

    await ask(canvasElement, 'Explain revenue while I check the notebook summary.');
    await canvas.findByText('Thinking…', {}, { timeout: 10000 });

    await selectConversation(canvasElement, /^Notebook summary/);
    await expect(await canvas.findByText('This notebook compares monthly revenue across regions.')).toBeVisible();

    await selectConversation(canvasElement, /^Revenue analysis/);
    await expect(await canvas.findByText('Explain revenue while I check the notebook summary.')).toBeVisible();
    const table = await canvas.findByRole('table', {}, { timeout: 10000 });
    await waitFor(() => expect(table.closest('article')).not.toHaveAttribute('aria-busy', 'true'), { timeout: 10000 });
    await expect(table).toBeVisible();
  }
};

export const CreationFailure: Story = {
  args: { demonstration: 'creation-failure' },
  parameters: {
    docs: {
      description: {
        story: 'Submits a first question and stops at conversation creation failure, ready for an explicit retry.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = await ask(canvasElement, 'Keep this question if creation fails.');
    await expect(await canvas.findByRole('alert', {}, { timeout: 10000 })).toBeVisible();
  }
};

export const SendRejectedAfterCreation: Story = {
  args: { demonstration: 'send-rejected' },
  parameters: {
    docs: {
      description: {
        story:
          'Creates a conversation and stops when its first send is rejected before acceptance. No retry is submitted.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = await ask(canvasElement, 'Keep this question if the send is rejected.');
    await expect(await canvas.findByRole('alert', {}, { timeout: 10000 })).toBeVisible();
  }
};

export const AcceptanceUnknown: Story = {
  args: { demonstration: 'acceptance-unknown' },
  parameters: {
    docs: {
      description: {
        story:
          'Stops at unknown acceptance, showing the warning and explicit Reload action without reloading or resending.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = await ask(canvasElement, 'Was this question accepted?');
    await expect(await canvas.findByRole('button', { name: 'Reload conversation' }, { timeout: 10000 })).toBeVisible();
    await expect(canvas.getAllByRole('alert')[0]).toBeVisible();
  }
};

export const AcceptedRunFailure: Story = {
  args: { demonstration: 'accepted-run-failure' },
  parameters: {
    docs: {
      description: {
        story: 'Stops after an accepted run fails, leaving the accepted question and inline answer failure visible.'
      }
    }
  },
  play: async ({ canvasElement }) => {
    const canvas = await ask(canvasElement, 'Read the revenue data.');
    await expect(await canvas.findByRole('article', { name: 'Your question' })).toBeVisible();
    const answer = await canvas.findByRole('article', { name: 'Assistant answer' });
    await expect(await within(answer).findByRole('alert', {}, { timeout: 10000 })).toBeVisible();
  }
};
