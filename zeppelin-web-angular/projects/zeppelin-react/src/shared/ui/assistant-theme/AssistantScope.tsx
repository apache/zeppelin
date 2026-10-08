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

import { assignInlineVars } from '@vanilla-extract/dynamic';
import { scope, themeVars } from './AssistantScope.css';

import type { CSSProperties, HTMLAttributes } from 'react';
import { theme, type GlobalToken } from 'antd';
import { readable } from './contrast';

/**
 * The antd class prefix for the assistant, apart from the host's ng-zorro `ant-` classes. The assistant's CSS names a
 * few antd classes through it (`zeppelin-ai-btn`, `zeppelin-ai-dropdown-menu-…`) to override their focus and colours.
 */
export const ASSISTANT_PREFIX_CLS = 'zeppelin-ai';

/** Zeppelin's own seeds (`@primary-color`, `@border-radius-base`), so antd derives both themes from them. */
export const ASSISTANT_SEED_TOKENS = {
  colorPrimary: '#3071a9',
  borderRadius: 4
} as const;

/**
 * The assistant palette derived from the host's resolved antd theme.
 * Components style themselves only through the theme contract, so light and dark need no per-component rules.
 */
export const assistantThemeValues = (token: GlobalToken) => {
  // AA where antd's palette falls short: text 4.5:1 on the panel and its tints, indicators 3:1 on the panel.
  const panel = token.colorBgContainer;
  const tints = [panel, token.colorFillQuaternary, token.colorPrimaryBg];
  const text = (color: string) => readable(color, tints, 4.5, token.colorTextBase);
  const indicator = (color: string) => readable(color, [panel], 3, token.colorTextBase);
  return {
    text: token.colorText,
    // antd's control size, which the host's dark reset would otherwise replace with the inherited text size.
    controlFontSize: `${token.fontSize}px`,
    textSecondary: token.colorTextSecondary,
    textTertiary: text(token.colorTextTertiary),
    bg: token.colorBgContainer,
    fill: token.colorFillQuaternary,
    fillStrong: token.colorFillTertiary,
    border: token.colorBorderSecondary,
    borderStrong: token.colorBorder,
    // The brand fill (marks, the streaming caret); focus rings and selection use focus.
    accent: token.colorPrimary,
    focus: indicator(token.colorPrimary),
    // Accent for text on the panel or on accentBg.
    accentText: text(token.colorPrimaryText),
    accentTextHover: text(token.colorPrimaryTextHover),
    accentBg: token.colorPrimaryBg,
    accentBorder: token.colorPrimaryBorder,
    success: indicator(token.colorSuccess),
    radiusSm: `${token.borderRadiusSM}px`,
    radius: `${token.borderRadius}px`,
    radiusLg: `${token.borderRadiusLG}px`,
    font: token.fontFamily,
    fontMono: token.fontFamilyCode,
    shadow: token.boxShadowTertiary
  };
};

export type AssistantScopeProps = HTMLAttributes<HTMLDivElement>;

export const assistantThemeStyles = (token: GlobalToken): CSSProperties =>
  assignInlineVars(themeVars, assistantThemeValues(token));

/** Root of every assistant surface: sets the tokens and the base type. Pieces render correctly only inside it. */
export const AssistantScope = ({ className, style, ...props }: AssistantScopeProps) => {
  const { token } = theme.useToken();
  return (
    <div
      {...props}
      className={className ? `${scope} ${className}` : scope}
      style={{ ...assistantThemeStyles(token), ...style }}
    />
  );
};
