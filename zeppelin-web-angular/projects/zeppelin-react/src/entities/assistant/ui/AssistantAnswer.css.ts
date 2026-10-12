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

import { globalStyle, keyframes, style } from '@vanilla-extract/css';
import { vars } from '@/shared/ui/assistant-theme/AssistantScope.css';

export const progress = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space2,
  color: vars.textSecondary,
  fontSize: vars.fontSizeSm
});

const pulse = keyframes({
  '0%, 80%, 100%': { opacity: 0.25 },
  '40%': { opacity: 1 }
});

export const dots = style({
  display: 'inline-flex',
  gap: 3
});

globalStyle(`${dots} > i`, {
  width: 4,
  height: 4,
  borderRadius: '50%',
  background: vars.accent,
  animation: `${pulse} 1.2s ${vars.easeStandard} infinite`
});
globalStyle(`${dots} > i:nth-child(2)`, { animationDelay: '0.15s' });
globalStyle(`${dots} > i:nth-child(3)`, { animationDelay: '0.3s' });

export const skeleton = style({
  marginTop: vars.space1
});

export const toolLog = style({
  border: `1px solid ${vars.border}`,
  borderRadius: vars.radius,
  background: vars.fill,
  fontSize: vars.fontSizeSm
});

export const toolSummary = style({
  padding: `${vars.space1} ${vars.space2}`,
  color: vars.textSecondary,
  cursor: 'pointer'
});

export const toolList = style({
  listStyle: 'none',
  margin: 0,
  padding: `0 ${vars.space2} ${vars.space2}`,
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space1
});

export const toolItem = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space2,
  color: vars.text
});

export const toolIcon = style({
  display: 'inline-flex',
  width: 14,
  justifyContent: 'center',
  color: vars.textSecondary
});

export const toolDone = style({
  color: vars.success
});

export const toolName = style({
  fontFamily: vars.fontMono,
  overflowWrap: 'anywhere'
});
