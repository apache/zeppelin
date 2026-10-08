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

import { useLayoutEffect, type ReactNode } from 'react';
import type { Preview } from '@storybook/react-vite';
import { ZeppelinThemeProvider } from '../src/theme/ZeppelinThemeProvider';
import { AssistantScope, ASSISTANT_PREFIX_CLS, ASSISTANT_SEED_TOKENS } from '../src/shared/ui/assistant-theme';

const StorySurface = ({ mode, children }: { mode: string; children: ReactNode }) => {
  useLayoutEffect(() => {
    document.documentElement.setAttribute('data-theme', mode);
    document.body.style.backgroundColor = mode === 'dark' ? '#141414' : '#fff';
  }, [mode]);
  return (
    <ZeppelinThemeProvider prefixCls={ASSISTANT_PREFIX_CLS} token={ASSISTANT_SEED_TOKENS}>
      <AssistantScope style={{ width: 370 }}>{children}</AssistantScope>
    </ZeppelinThemeProvider>
  );
};
const preview: Preview = {
  initialGlobals: { theme: 'light' },
  globalTypes: {
    theme: {
      description: 'Assistant theme',
      toolbar: { icon: 'circlehollow', items: ['light', 'dark'], dynamicTitle: true }
    }
  },
  decorators: [
    (Story, context) => (
      <StorySurface mode={context.globals['theme'] === 'dark' ? 'dark' : 'light'}>
        <Story />
      </StorySurface>
    )
  ],
  parameters: { layout: 'padded' }
};
export default preview;
