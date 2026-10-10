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

import { Button, Space, Typography } from 'antd';
import type { Meta, StoryObj } from '@storybook/react-vite';

const ThemeSurface = () => (
  <Space direction="vertical">
    <Typography.Text>Assistant theme</Typography.Text>
    <Button type="primary">Primary action</Button>
    <Button>Secondary action</Button>
    <Typography.Text type="secondary">Secondary text</Typography.Text>
  </Space>
);

const meta = {
  title: 'Assistant/Setup',
  component: ThemeSurface,
  parameters: {
    docs: { description: { component: 'Temporary theme check. Remove after the Assistant UI stories land.' } }
  }
} satisfies Meta<typeof ThemeSurface>;

export default meta;
type Story = StoryObj<typeof meta>;
export const TemporaryThemeCheck: Story = {};
