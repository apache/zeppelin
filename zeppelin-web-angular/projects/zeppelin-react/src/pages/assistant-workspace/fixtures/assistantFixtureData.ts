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

import type { AssistantConversation, AssistantMessage } from '@/entities/assistant/model/assistantContract';

/** Fixed reference clock for reproducible conversation dates. */
export const FIXTURE_NOW = Date.UTC(2026, 9, 19, 12);

export interface AssistantFixtureConversation {
  conversation: AssistantConversation;
  latest: readonly AssistantMessage[];
  earlier?: readonly AssistantMessage[];
}

const revenueConversation: AssistantConversation = {
  id: 'c1',
  title: 'Revenue analysis',
  ownerId: 'demo-user',
  canSendMessage: true,
  updatedAt: new Date(FIXTURE_NOW - 60_000).toISOString()
};

const notebookSummary: AssistantFixtureConversation = {
  conversation: {
    id: 'c2',
    title: 'Notebook summary',
    ownerId: 'demo-user',
    canSendMessage: true,
    updatedAt: new Date(FIXTURE_NOW - 86_400_000).toISOString()
  },
  latest: [
    { id: 'c2-user-1', role: 'user', content: 'Summarize this notebook.' },
    {
      id: 'c2-answer-1',
      role: 'assistant',
      segments: [{ id: 'c2-answer-1', content: 'This notebook compares monthly revenue across regions.' }]
    }
  ]
};

const sharedAnalysis: AssistantFixtureConversation = {
  conversation: {
    id: 'c3',
    title: 'Shared analysis',
    ownerId: 'teammate',
    canSendMessage: false,
    updatedAt: new Date(FIXTURE_NOW - 172_800_000).toISOString()
  },
  latest: [
    {
      id: 'c3-answer-1',
      role: 'assistant',
      segments: [{ id: 'c3-answer-1', content: 'This shared conversation can be read, but not changed.' }]
    }
  ]
};

/** Compact writable and read-only conversations for trying the panel. */
export const ASSISTANT_DEMO_CONVERSATIONS: readonly AssistantFixtureConversation[] = [
  {
    conversation: revenueConversation,
    latest: [
      { id: 'c1-user-2', role: 'user', content: 'Which region should we investigate next?' },
      {
        id: 'c1-answer-2',
        role: 'assistant',
        segments: [{ id: 'c1-answer-2', content: 'Compare Europe’s order volume with its average order value.' }]
      }
    ]
  },
  notebookSummary,
  sharedAnalysis
];

/** Two chronological pages with an answer continuing across the history boundary. */
export const ASSISTANT_HISTORY_CONVERSATIONS: readonly AssistantFixtureConversation[] = [
  {
    conversation: revenueConversation,
    earlier: [
      { id: 'c1-user-1', role: 'user', content: 'How did revenue change by region?' },
      {
        id: 'c1-answer-1a',
        role: 'assistant',
        segments: [
          {
            id: 'c1-answer-1a',
            content: 'I checked the regional revenue paragraph.',
            toolCalls: [{ id: 'c1-tool-1', name: 'read_notebook' }]
          }
        ]
      }
    ],
    latest: [
      {
        id: 'c1-answer-1b',
        role: 'assistant',
        segments: [{ id: 'c1-answer-1b', content: 'Revenue increased in all three regions, led by Europe.' }]
      },
      { id: 'c1-user-2', role: 'user', content: 'Which region should we investigate next?' },
      {
        id: 'c1-answer-2',
        role: 'assistant',
        segments: [
          {
            id: 'c1-answer-2',
            content: [
              'Compare Europe’s order volume with its average order value.',
              'Start with the monthly totals in the regional revenue paragraph. Use the same reporting period for each region.',
              'Then separate the change in order count from the change in average order value. Either can explain revenue growth.',
              'Check whether the regional mix changed. A larger share of high-value orders can raise revenue without a similar rise in volume.',
              'Compare those observations with the Americas and Asia Pacific before drawing a conclusion about Europe.',
              'The earlier answer describes the initial regional comparison. Scroll to the top to read that part of this conversation.'
            ].join('\n\n')
          }
        ]
      }
    ]
  },
  notebookSummary,
  sharedAnalysis
];
