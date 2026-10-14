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

import { globalStyle, style } from '@vanilla-extract/css';
import { vars } from '@/shared/ui/assistant-theme/AssistantScope.css';

export const answer = style({
  boxSizing: 'border-box',
  width: '100%',
  minWidth: 0,
  maxWidth: '100%',
  color: vars.text,
  fontFamily: vars.font,
  fontSize: vars.fontSize,
  lineHeight: vars.lineHeight,
  overflowWrap: 'anywhere'
});

globalStyle(`${answer} *, ${answer} *::before, ${answer} *::after`, {
  boxSizing: 'inherit'
});

globalStyle(`${answer} > :first-child`, { marginTop: 0 });
globalStyle(`${answer} > :last-child`, { marginBottom: 0 });

globalStyle(`${answer} p`, { margin: `0 0 ${vars.space3}` });
globalStyle(`${answer} h1, ${answer} h2, ${answer} h3, ${answer} h4, ${answer} h5, ${answer} h6`, {
  margin: `${vars.space4} 0 ${vars.space2}`,
  color: vars.text,
  fontWeight: 600,
  lineHeight: 1.35
});
globalStyle(`${answer} h1`, { fontSize: '1.6em' });
globalStyle(`${answer} h2`, { fontSize: '1.4em' });
globalStyle(`${answer} h3`, { fontSize: '1.2em' });
globalStyle(`${answer} h4, ${answer} h5, ${answer} h6`, { fontSize: '1em' });

globalStyle(`${answer} ul, ${answer} ol`, {
  margin: `0 0 ${vars.space3}`,
  paddingLeft: vars.space5
});
globalStyle(`${answer} li + li`, { marginTop: vars.space1 });
globalStyle(`${answer} li > p`, { marginBottom: vars.space1 });
globalStyle(`${answer} li > ul, ${answer} li > ol`, {
  marginTop: vars.space1,
  marginBottom: 0
});
globalStyle(`${answer} input[type="checkbox"]`, {
  marginRight: vars.space1,
  accentColor: vars.accent
});

globalStyle(`${answer} blockquote`, {
  margin: `0 0 ${vars.space3}`,
  padding: `${vars.space1} 0 ${vars.space1} ${vars.space3}`,
  borderLeft: `3px solid ${vars.borderStrong}`,
  color: vars.textSecondary
});
globalStyle(`${answer} blockquote > :last-child`, { marginBottom: 0 });
globalStyle(`${answer} hr`, {
  margin: `${vars.space4} 0`,
  border: 0,
  borderTop: `1px solid ${vars.border}`
});

globalStyle(`${answer} a`, {
  color: vars.accentText,
  textDecoration: 'underline',
  textUnderlineOffset: '2px'
});
globalStyle(`${answer} a:hover`, { color: vars.accentTextHover });
globalStyle(`${answer} :focus-visible`, {
  outline: `2px solid ${vars.focus}`,
  outlineOffset: 2
});

globalStyle(`${answer} code`, {
  padding: `1px ${vars.space1}`,
  borderRadius: vars.radiusSm,
  background: vars.fill,
  color: vars.text,
  fontFamily: vars.fontMono,
  fontSize: vars.fontSizeSm
});

export const paragraphReference = style({
  display: 'inline',
  maxWidth: '100%',
  margin: 0,
  padding: `0 ${vars.space1}`,
  border: `1px solid ${vars.accentBorder}`,
  borderRadius: vars.radiusSm,
  background: vars.accentBg,
  color: vars.accentText,
  font: 'inherit',
  lineHeight: 'inherit',
  textAlign: 'inherit',
  whiteSpace: 'normal',
  overflowWrap: 'anywhere',
  verticalAlign: 'baseline',
  cursor: 'pointer',
  selectors: {
    '&:hover': { color: vars.accentTextHover },
    '&:focus-visible': { outline: `2px solid ${vars.focus}`, outlineOffset: 2 }
  }
});

export const codeBlock = style({
  minWidth: 0,
  maxWidth: '100%',
  marginBottom: vars.space3,
  border: `1px solid ${vars.border}`,
  borderRadius: vars.radius,
  background: vars.fill
});

export const codeLanguage = style({
  padding: `${vars.space1} ${vars.space3}`,
  borderBottom: `1px solid ${vars.border}`,
  color: vars.textSecondary,
  fontFamily: vars.fontMono,
  fontSize: vars.fontSizeXs,
  overflowWrap: 'anywhere'
});

export const codeSource = style({
  maxWidth: '100%',
  margin: 0,
  padding: vars.space3,
  overflowX: 'auto',
  overscrollBehaviorX: 'contain',
  whiteSpace: 'pre',
  overflowWrap: 'normal',
  color: vars.text,
  fontFamily: vars.fontMono,
  fontSize: vars.fontSizeSm,
  lineHeight: vars.lineHeight
});

globalStyle(`${codeSource} code`, {
  padding: 0,
  borderRadius: 0,
  background: 'none',
  fontSize: 'inherit',
  whiteSpace: 'inherit'
});

export const tableScroll = style({
  minWidth: 0,
  maxWidth: '100%',
  marginBottom: vars.space3,
  overflowX: 'auto',
  overscrollBehaviorX: 'contain',
  border: `1px solid ${vars.border}`,
  borderRadius: vars.radius
});

globalStyle(`${tableScroll} table`, {
  width: '100%',
  borderCollapse: 'collapse',
  fontSize: vars.fontSizeSm
});
globalStyle(`${tableScroll} th, ${tableScroll} td`, {
  padding: `${vars.space2} ${vars.space3}`,
  borderBottom: `1px solid ${vars.border}`,
  borderRight: `1px solid ${vars.border}`,
  textAlign: 'left',
  verticalAlign: 'top',
  whiteSpace: 'nowrap',
  overflowWrap: 'normal'
});
globalStyle(`${tableScroll} th`, {
  background: vars.fill,
  color: vars.text,
  fontWeight: 600
});
globalStyle(`${tableScroll} th:last-child, ${tableScroll} td:last-child`, { borderRight: 0 });
globalStyle(`${tableScroll} tbody tr:last-child td`, { borderBottom: 0 });
