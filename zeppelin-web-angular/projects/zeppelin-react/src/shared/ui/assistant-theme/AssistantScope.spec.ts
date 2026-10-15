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

import { theme } from 'antd';
import { describe, expect, it } from 'vitest';
import { ASSISTANT_SEED_TOKENS, assistantThemeValues } from './AssistantScope';
import { composite, contrastRatio } from './contrast';

describe.each([
  ['light', theme.defaultAlgorithm],
  ['dark', theme.darkAlgorithm]
])('assistant tokens in the %s theme', (_name, algorithm) => {
  const token = theme.getDesignToken({ token: ASSISTANT_SEED_TOKENS, algorithm });
  const variables = assistantThemeValues(token);
  const panel = token.colorBgContainer;
  const chip = composite(token.colorPrimaryBg, panel);

  // The names that fall short, so a failure says which token broke.
  const below = (names: Array<keyof typeof variables>, backgrounds: string[], min: number) =>
    names.filter(name => backgrounds.some(background => contrastRatio(variables[name], background) < min));

  it('keeps small text at 4.5:1 on the panel and on the accent tint', () => {
    expect(below(['accentText', 'accentTextHover', 'textTertiary'], [panel, chip], 4.5)).toEqual([]);
  });

  it('keeps focus rings and status icons at 3:1 on the panel', () => {
    expect(below(['focus', 'success'], [panel], 3)).toEqual([]);
  });
});
