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

import { globalStyle, style } from '@vanilla-extract/css';
import { scope, vars } from '@/shared/ui/assistant-theme/AssistantScope.css';

export const composer = style({
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space2
});

export const note = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space2,
  margin: 0,
  color: vars.textSecondary,
  fontSize: vars.fontSizeSm
});

export const field = style({
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space1,
  padding: vars.space2,
  borderRadius: vars.radiusLg,
  border: `1px solid ${vars.border}`,
  background: vars.bg,
  transition: `border-color ${vars.durationFast} ${vars.easeStandard}, box-shadow ${vars.durationFast} ${vars.easeStandard}`,
  selectors: {
    // The field draws the focus ring for the textarea inside it.
    '&:focus-within': { borderColor: vars.focus, boxShadow: `0 0 0 1px ${vars.focus}` },
    '&[data-disabled]': { background: vars.fill }
  }
});

export const input = style({
  padding: `${vars.space1} ${vars.space2}`,
  fontSize: vars.fontSize
});

globalStyle(`${scope} ${input}:focus-visible`, { outline: 'none' });

export const footer = style({
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: vars.space2,
  paddingLeft: vars.space2
});

export const hint = style({
  color: vars.textTertiary,
  fontSize: vars.fontSizeXs
});
