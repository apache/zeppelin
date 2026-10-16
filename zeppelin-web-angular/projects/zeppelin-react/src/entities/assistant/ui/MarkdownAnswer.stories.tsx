/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useCallback, useState } from 'react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';
import { MarkdownAnswer, type MarkdownAnswerProps } from './MarkdownAnswer';

const paragraphId = 'paragraph_1423500782552_-1439281894';
const describeParagraph = (id: string) =>
  id === paragraphId ? { text: 'Paragraph 1', name: 'Sales query' } : undefined;

const streamingChunks = [
  '## Sales summary\n\n```sql\nSELECT product,',
  ' SUM(revenue) AS total_revenue\nFROM sales\n',
  'GROUP BY product;\n```\n\nRevenue is grouped by product.'
];

const ParagraphReferenceExample = ({ onOpenParagraph, ...args }: MarkdownAnswerProps) => {
  const [selectedParagraph, setSelectedParagraph] = useState('No paragraph selected.');

  const handleOpenParagraph = useCallback(
    (id: string, label: string) => {
      onOpenParagraph?.(id, label);
      setSelectedParagraph(`Selected ${label} (${id}).`);
    },
    [onOpenParagraph]
  );

  return (
    <div>
      <MarkdownAnswer {...args} onOpenParagraph={handleOpenParagraph} />
      <p role="status">{selectedParagraph}</p>
    </div>
  );
};

const StreamingExample = (args: MarkdownAnswerProps) => {
  const [step, setStep] = useState(0);
  const completed = step === streamingChunks.length - 1;
  const content = streamingChunks.slice(0, step + 1).join('');

  return (
    <div>
      <button type="button" disabled={completed} onClick={() => setStep(current => current + 1)}>
        {completed ? 'Answer complete' : 'Append next chunk'}
      </button>
      <button type="button" disabled={step === 0} onClick={() => setStep(0)} style={{ marginLeft: 8 }}>
        Restart stream
      </button>
      <p role="status">
        Chunk {step + 1} of {streamingChunks.length}
      </p>
      <MarkdownAnswer {...args} content={content} />
    </div>
  );
};

const meta = {
  title: 'Assistant/MarkdownAnswer',
  component: MarkdownAnswer,
  args: { content: '' },
  parameters: {
    docs: {
      description: {
        component:
          'Safe Markdown answers in the assistant sidebar. Tables and code scroll within the answer; paragraph references invoke a callback instead of navigating.'
      }
    }
  }
} satisfies Meta<typeof MarkdownAnswer>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompletedAnswer: Story = {
  render: args => <ParagraphReferenceExample {...args} />,
  args: {
    content: [
      '### Regional sales',
      '',
      'Northern Europe generated the highest revenue.',
      '',
      '| Region | Revenue |',
      '| :--- | ---: |',
      '| Northern Europe | 4200 |',
      '| Asia Pacific | 2100 |',
      '| North America | 1600 |',
      '',
      'Use this query to compare the regions:',
      '',
      '```sql',
      `-- Source: ${paragraphId}`,
      'SELECT sales_region, SUM(net_revenue_amount) AS revenue',
      'FROM analytics.sales_transactions',
      'GROUP BY sales_region;',
      '```',
      '',
      `> [Inspect the source query](#${paragraphId}) before comparing other quarters.`,
      '',
      '[Zeppelin documentation](https://zeppelin.apache.org/docs/latest/)'
    ].join('\n'),
    describeParagraph,
    onOpenParagraph: fn()
  }
};

export const GfmTable: Story = {
  decorators: [
    Story => (
      <div style={{ width: 280, maxWidth: '100%' }}>
        <Story />
      </div>
    )
  ],
  args: {
    content: [
      '### Regional sales',
      '',
      'Revenue by product and region:',
      '',
      '| Product | Region | Quarter | Revenue | Orders | Average order | Source dataset |',
      '| :--- | :---: | :---: | ---: | ---: | ---: | :--- |',
      '| Enterprise notebook subscription | Northern Europe | Q1 | 128000 | 320 | 400 | analytics.sales_transactions_2026_q1 |',
      '| Managed query workspace | Asia Pacific | Q2 | 96000 | 240 | 400 | analytics.sales_transactions_2026_q2 |',
      '| Interactive report package | North America | Q3 | 144000 | 360 | 400 | analytics.sales_transactions_2026_q3 |',
      '',
      'Interactive reports generated the highest revenue.'
    ].join('\n')
  }
};

export const SqlCode: Story = {
  args: {
    content: [
      '### Revenue query',
      '',
      'Use this query to compare products and regions for the first quarter:',
      '',
      '```sql',
      'SELECT product_name, sales_region, fiscal_quarter, SUM(net_revenue_amount) AS total_net_revenue, COUNT(DISTINCT customer_account_identifier) AS distinct_customer_accounts',
      'FROM analytics.sales_transactions',
      "WHERE fiscal_quarter = '2026-Q1'",
      'GROUP BY product_name, sales_region, fiscal_quarter;',
      '```',
      '',
      'Run the query in a notebook paragraph to inspect the result.'
    ].join('\n')
  }
};

export const PartialFence: Story = {
  args: {
    content: [
      '### Revenue query',
      '',
      '```sql',
      'SELECT product, SUM(revenue) AS total_revenue',
      'FROM sales',
      'WHERE',
      ''
    ].join('\n')
  }
};

export const ParagraphReference: Story = {
  render: args => <ParagraphReferenceExample {...args} />,
  args: {
    content: [
      '### Source query',
      '',
      `The revenue calculation uses ${paragraphId}.`,
      '',
      'The earlier query paragraph_1423500782554_-99 is no longer available.',
      '',
      '[Zeppelin documentation](https://zeppelin.apache.org/docs/latest/)'
    ].join('\n'),
    describeParagraph,
    onOpenParagraph: fn()
  }
};

export const DangerousContent: Story = {
  args: {
    content: [
      '### Untrusted answer content',
      '',
      '<script>alert("This must stay text")</script>',
      '',
      '<img src="https://example.com/tracker.png" onerror="alert(1)">',
      '',
      '<button onclick="alert(1)">This is not an executable button</button>',
      '',
      '[JavaScript link](javascript:alert%281%29)',
      '',
      '[Safe Zeppelin documentation](https://zeppelin.apache.org/docs/latest/)'
    ].join('\n')
  }
};

export const Footnotes: Story = {
  args: {
    content: [
      '### Revenue',
      '',
      'Northern Europe generated the highest revenue[^1], ahead of Asia Pacific[^2].',
      '',
      '[^1]: Source: the finance warehouse, closed quarters only.',
      '[^2]: Converted at the quarter-end exchange rate.'
    ].join('\n')
  }
};

export const Streaming: Story = {
  render: args => <StreamingExample {...args} />,
  args: { content: streamingChunks[0] },
  argTypes: { content: { control: false } },
  parameters: {
    docs: {
      description: {
        story: 'Append chunks to an unclosed SQL fence, complete the answer, or restart the stream.'
      }
    }
  }
};
