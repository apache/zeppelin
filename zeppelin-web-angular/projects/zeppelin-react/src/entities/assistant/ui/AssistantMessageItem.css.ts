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
import { enter, vars } from '@/shared/ui/assistant-theme/AssistantScope.css';

export const message = style({
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space1,
  minWidth: 0,
  animation: `${enter} ${vars.durationModerate} ${vars.easeEntrance}`
});

export const user = style({
  alignSelf: 'flex-end',
  alignItems: 'flex-end',
  maxWidth: '85%'
});

export const assistant = style({
  alignSelf: 'stretch'
});

export const label = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space2,
  color: vars.textSecondary,
  fontSize: vars.fontSizeSm
});

export const badge = style({
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 20,
  height: 20,
  borderRadius: vars.radiusSm,
  background: vars.accent,
  color: vars.bg,
  fontSize: vars.fontSizeXs,
  fontWeight: 600
});

export const bubble = style({
  padding: `${vars.space2} ${vars.space3}`,
  borderRadius: vars.radiusLg,
  border: `1px solid ${vars.accentBorder}`,
  background: vars.accentBg,
  overflowWrap: 'anywhere'
});

export const body = style({
  minWidth: 0,
  overflowWrap: 'anywhere'
});

export const plainText = style({
  whiteSpace: 'pre-wrap'
});

const blink = keyframes({
  '50%': { opacity: 0 }
});

/**
 * Trails the partial answer while it streams. The renderer wraps the answer in blocks, so the caret goes inside the
 * last block (or the last item of a closing list) to stay on the line where the text ends.
 */
globalStyle(
  `${body}[data-streaming] > :last-child > :last-child::after, ${body}[data-streaming] > :last-child > :is(ul, ol):last-child > li:last-child::after`,
  {
    content: '""',
    display: 'inline-block',
    width: 2,
    height: '1em',
    marginLeft: 2,
    verticalAlign: 'text-bottom',
    background: vars.accent,
    animation: `${blink} 1s steps(1) infinite`
  }
);
globalStyle(`${body}[data-streaming] > :last-child > :is(ul, ol):last-child::after`, { content: 'none' });
