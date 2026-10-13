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
import { useState } from 'react';
import { fn } from 'storybook/test';
import { AssistantComposer } from './AssistantComposer';

const meta = {
  title: 'Assistant/Composer',
  component: AssistantComposer,
  args: { value: '', onChange: fn(), onSubmit: fn() }
} satisfies Meta<typeof AssistantComposer>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Empty: Story = {};

export const Draft: Story = {
  args: { value: 'Explain how this notebook calculates revenue.' }
};

export const WhitespaceOnly: Story = {
  args: { value: '   \n  ' }
};

export const Sending: Story = {
  args: { value: 'Explain how this notebook calculates revenue.', sending: true }
};

export const ReadOnly: Story = {
  args: { readOnly: true, readOnlyReason: 'This conversation belongs to another user.' }
};

/** Type and send: the question is logged in the Actions panel and the input is cleared, as a parent would do. */
export const Playground: Story = {
  render: function Render(args) {
    const [value, setValue] = useState(args.value);
    return (
      <AssistantComposer
        {...args}
        value={value}
        onChange={next => {
          setValue(next);
          args.onChange(next);
        }}
        onSubmit={question => {
          args.onSubmit(question);
          setValue('');
        }}
      />
    );
  }
};
