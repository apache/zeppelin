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
import { vars } from '@/shared/ui/assistant-theme/AssistantScope.css';

export const nav = style({
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 40,
  height: 32,
  padding: 0,
  border: 0,
  background: 'transparent',
  cursor: 'pointer'
});

export const navIcon = style({
  display: 'inline-grid',
  placeItems: 'center',
  width: 20,
  height: 16,
  border: `1px solid ${vars.borderStrong}`,
  borderRadius: 3,
  background: vars.bg,
  color: vars.text,
  font: '700 10px/1 Arial, sans-serif'
});

globalStyle(`${nav}[aria-pressed='true'] ${navIcon}`, { borderColor: vars.accent });

export const panel = style({
  position: 'sticky',
  zIndex: 25,
  top: 0,
  width: 'calc(var(--assistant-sidebar-width) - 40px)',
  height: 'calc(100dvh - var(--assistant-panel-top, 0px))',
  display: 'flex',
  flexDirection: 'column',
  minHeight: 0,
  overflow: 'hidden',
  borderRight: `1px solid ${vars.border}`,
  background: vars.bg,
  boxShadow: '4px 0 2px 0 rgba(0, 0, 0, 0.06)',
  paddingTop: vars.space2,
  '@media': {
    '(max-width: 640px)': {
      width: 'min(var(--assistant-sidebar-width), 100vw)',
      marginRight: 'calc(-1 * min(var(--assistant-sidebar-width), 100vw))',
      marginLeft: -40
    }
  }
});

export const header = style({
  display: 'flex',
  alignItems: 'center',
  // The scope's border-box includes the padding and border, which the 36px content height excluded.
  minHeight: 45,
  padding: `${vars.space1} ${vars.space2} ${vars.space1} ${vars.space3}`,
  borderBottom: `1px solid ${vars.border}`
});

export const title = style({
  margin: 0,
  fontSize: 'inherit',
  fontWeight: 600
});

export const close = style({
  position: 'absolute',
  zIndex: 1,
  top: 3,
  right: 3,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 40,
  height: 50,
  padding: 0,
  border: 0,
  background: 'transparent',
  color: vars.textSecondary,
  cursor: 'pointer',
  fontSize: 20,
  transition: `color ${vars.durationFast} ${vars.easeStandard}`,
  selectors: {
    '&:hover': { color: vars.accentTextHover }
  }
});

export const resize = style({
  position: 'absolute',
  top: 0,
  right: 0,
  bottom: 0,
  width: 5,
  cursor: 'col-resize',
  touchAction: 'none',
  '@media': {
    '(max-width: 640px)': { display: 'none' }
  }
});
