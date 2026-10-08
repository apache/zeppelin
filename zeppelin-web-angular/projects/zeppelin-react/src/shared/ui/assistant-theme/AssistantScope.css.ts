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

import { createThemeContract, createVar, globalStyle, keyframes, style } from '@vanilla-extract/css';

export const themeVars = createThemeContract({
  text: null,
  controlFontSize: null,
  textSecondary: null,
  textTertiary: null,
  bg: null,
  fill: null,
  fillStrong: null,
  border: null,
  borderStrong: null,
  accent: null,
  focus: null,
  accentText: null,
  accentTextHover: null,
  accentBg: null,
  accentBorder: null,
  success: null,
  radiusSm: null,
  radius: null,
  radiusLg: null,
  font: null,
  fontMono: null,
  shadow: null
});

export const vars = {
  ...themeVars,
  fontSize: '13px',
  fontSizeSm: '12px',
  fontSizeXs: '11px',
  lineHeight: '1.6',
  space1: '4px',
  space2: '8px',
  space3: '12px',
  space4: '16px',
  space5: '24px',
  durationFast: '110ms',
  durationModerate: '240ms',
  durationSlow: '400ms',
  easeStandard: 'cubic-bezier(0.2, 0, 0.38, 0.9)',
  easeEntrance: 'cubic-bezier(0, 0, 0.38, 0.9)',
  aiGradient: createVar(),
  iconBorder: createVar()
};

export const scope = style({
  boxSizing: 'border-box',
  color: vars.text,
  fontFamily: vars.font,
  fontSize: vars.fontSize,
  lineHeight: vars.lineHeight
});

globalStyle(`${scope} *, ${scope} *::before, ${scope} *::after`, { boxSizing: 'inherit' });
globalStyle(`${scope} :focus-visible`, { outline: `2px solid ${vars.focus}`, outlineOffset: 1 });
globalStyle(`${scope} .zeppelin-ai-btn`, { fontSize: vars.controlFontSize });
globalStyle(`${scope} .zeppelin-ai-btn:not(:disabled):focus-visible`, {
  outline: `2px solid ${vars.focus}`,
  outlineOffset: 1
});
globalStyle(`${scope} :focus:not(:focus-visible)`, { outline: 'none' });

export const visuallyHidden = style({
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap'
});
export const enter = keyframes({
  from: { opacity: 0, transform: 'translateY(4px)' },
  to: { opacity: 1, transform: 'none' }
});
export const pop = keyframes({
  '0%': { opacity: 0, transform: 'scale(0.6)' },
  '70%': { opacity: 1, transform: 'scale(1.12)' },
  '100%': { transform: 'none' }
});
globalStyle(`${scope} *, ${scope} *::before, ${scope} *::after`, {
  '@media': {
    '(prefers-reduced-motion: reduce)': {
      animationDuration: '1ms !important',
      animationIterationCount: '1 !important',
      transitionDuration: '1ms !important'
    }
  }
});
