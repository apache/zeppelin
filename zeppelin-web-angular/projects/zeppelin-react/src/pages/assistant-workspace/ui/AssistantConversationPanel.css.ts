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
import { vars } from '@/shared/ui/assistant-theme/AssistantScope.css';

export const panel = style({
  position: 'relative',
  display: 'flex',
  flex: 1,
  minWidth: 0,
  minHeight: 0,
  height: '100%',
  overflow: 'hidden',
  color: vars.text,
  background: vars.bg
});

export const content = style({
  display: 'flex',
  flex: 1,
  minWidth: 0,
  minHeight: 0
});

export const conversation = style({
  display: 'flex',
  flexDirection: 'column',
  flex: 1,
  minWidth: 0,
  minHeight: 0
});

export const hidden = style({ display: 'none' });

export const messages = style({
  flex: '1 1 0',
  minWidth: 0,
  minHeight: 0,
  overflowY: 'auto',
  overflowX: 'hidden',
  overflowAnchor: 'none',
  overscrollBehavior: 'contain',
  scrollbarGutter: 'stable',
  padding: `${vars.space4} ${vars.space3} ${vars.space2}`
});

export const messageStack = style({
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space4,
  minWidth: 0
});

export const messageRow = style({
  display: 'flex',
  flexDirection: 'column',
  minWidth: 0
});

export const statusArea = style({
  flexShrink: 1,
  minHeight: 0,
  maxHeight: '40%',
  overflowY: 'auto',
  overscrollBehavior: 'contain'
});

export const note = style({
  flexShrink: 0,
  margin: 0,
  padding: `${vars.space2} ${vars.space3}`,
  color: vars.textSecondary,
  fontSize: vars.fontSizeSm,
  overflowWrap: 'anywhere'
});

export const empty = style({
  margin: 0,
  padding: `${vars.space5} ${vars.space3}`,
  color: vars.textSecondary,
  textAlign: 'center'
});

export const warning = style({
  flexShrink: 0,
  margin: vars.space2,
  padding: vars.space2,
  color: vars.text,
  background: vars.fill,
  border: `1px solid ${vars.borderStrong}`,
  borderRadius: vars.radiusSm,
  fontSize: vars.fontSizeSm,
  overflowWrap: 'anywhere'
});

export const composer = style({
  flexShrink: 0,
  minWidth: 0,
  margin: 0,
  padding: 0,
  border: 0
});

export const listView = style({
  flex: 1,
  minWidth: 0,
  minHeight: 0,
  overflowY: 'auto',
  overflowX: 'hidden',
  overscrollBehavior: 'contain',
  padding: vars.space2
});

export const listNavigation = style({ paddingBottom: vars.space2 });

export const action = style({
  minHeight: 30,
  maxWidth: '100%',
  padding: `${vars.space1} ${vars.space2}`,
  border: `1px solid ${vars.borderStrong}`,
  borderRadius: vars.radiusSm,
  color: vars.text,
  background: vars.bg,
  font: 'inherit',
  fontSize: vars.fontSizeSm,
  lineHeight: vars.lineHeight,
  cursor: 'pointer',
  overflowWrap: 'anywhere',
  selectors: {
    '&:hover:not(:disabled)': { background: vars.fill },
    '&:disabled': { cursor: 'not-allowed', opacity: 0.6 }
  }
});

export const primaryAction = style([
  action,
  {
    color: vars.accentText,
    borderColor: vars.accentBorder,
    background: vars.accentBg
  }
]);

export const confirmationBackdrop = style({
  position: 'absolute',
  inset: 0,
  zIndex: 1,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: vars.space3,
  background: vars.bg,
  overflowY: 'auto',
  overscrollBehavior: 'contain'
});

export const confirmation = style({
  width: '100%',
  maxWidth: 360,
  maxHeight: '100%',
  overflowY: 'auto',
  padding: vars.space3,
  border: `1px solid ${vars.borderStrong}`,
  borderRadius: vars.radius,
  background: vars.bg,
  boxShadow: vars.shadow,
  overflowWrap: 'anywhere'
});

export const confirmationTitle = style({
  margin: `0 0 ${vars.space2}`,
  fontSize: vars.fontSize,
  color: vars.text
});

export const confirmationDescription = style({
  margin: `0 0 ${vars.space3}`,
  color: vars.textSecondary
});

export const confirmationActions = style({
  display: 'flex',
  flexWrap: 'wrap',
  justifyContent: 'flex-end',
  gap: vars.space2
});
