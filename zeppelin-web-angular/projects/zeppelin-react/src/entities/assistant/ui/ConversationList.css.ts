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

import { style } from '@vanilla-extract/css';
import { vars, visuallyHidden } from '@/shared/ui/assistant-theme/AssistantScope.css';

export { visuallyHidden };

export const list = style({
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space2
});

export const header = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space2,
  padding: `${vars.space1} 0`
});

export const title = style({
  flex: 1,
  margin: 0,
  fontSize: vars.fontSize,
  fontWeight: 600
});

export const search = style({
  padding: `0 ${vars.space1}`
});

export const sectionLabel = style({
  margin: `${vars.space3} 0 0`,
  padding: `0 ${vars.space2}`,
  color: vars.textSecondary,
  fontSize: vars.fontSizeSm,
  selectors: {
    '&:first-child': { marginTop: 0 }
  }
});

export const items = style({
  listStyle: 'none',
  marginTop: `calc(-1 * ${vars.space2})`,
  marginBottom: 0,
  padding: 0,
  display: 'flex',
  flexDirection: 'column'
});

export const row = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space1
});

export const rowButton = style({
  flex: 1,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  minWidth: 0,
  padding: vars.space2,
  border: 'none',
  background: 'none',
  color: 'inherit',
  font: 'inherit',
  textAlign: 'left',
  borderRadius: vars.radius,
  cursor: 'pointer',
  selectors: {
    '&:hover': { background: vars.fill },
    '&[aria-current="true"]': { background: vars.accentBg, color: vars.accentText }
  }
});

export const rowTitle = style({
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
  width: '100%'
});

export const rowMeta = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space1,
  color: vars.textTertiary,
  fontSize: vars.fontSizeXs
});

export const empty = style({
  padding: vars.space4,
  textAlign: 'center',
  color: vars.textSecondary
});
