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

import { act } from 'react';
import { render, screen } from '@testing-library/react';
import { Button, theme as antdTheme } from 'antd';
import { afterEach, describe, expect, it } from 'vitest';
import { useHostThemeMode, ZeppelinThemeProvider } from './ZeppelinThemeProvider';
import { HostThemeMode } from './hostTheme';

const Probe = () => {
  const { token } = antdTheme.useToken();
  return (
    <>
      <span data-testid="container-bg">{token.colorBgContainer}</span>
      <span data-testid="font">{token.fontFamily}</span>
      <span data-testid="mode">{useHostThemeMode()}</span>
    </>
  );
};

const setHostTheme = (mode: HostThemeMode) => {
  document.documentElement.setAttribute('data-theme', mode);
};

describe('ZeppelinThemeProvider', () => {
  it('uses an isolated default prefix and allows a surface to override it', () => {
    render(
      <>
        <ZeppelinThemeProvider prefixCls="custom-surface">
          <Button>Custom control</Button>
        </ZeppelinThemeProvider>
        <ZeppelinThemeProvider>
          <Button>Default control</Button>
        </ZeppelinThemeProvider>
      </>
    );
    expect(screen.getByRole('button', { name: 'Custom control' }).classList.contains('custom-surface-btn')).toBe(true);
    expect(screen.getByRole('button', { name: 'Default control' }).classList.contains('zeppelin-react-btn')).toBe(true);
  });

  afterEach(() => {
    document.documentElement.removeAttribute('data-theme');
  });

  it('builds antd tokens from the dark algorithm when the shell is dark', () => {
    setHostTheme('dark');
    render(
      <ZeppelinThemeProvider>
        <Probe />
      </ZeppelinThemeProvider>
    );

    expect(screen.getByTestId('container-bg').textContent).toBe('#141414');
    expect(screen.getByTestId('mode').textContent).toBe('dark');
  });

  it('builds antd tokens from the default algorithm when the shell is light', () => {
    setHostTheme('light');
    render(
      <ZeppelinThemeProvider>
        <Probe />
      </ZeppelinThemeProvider>
    );

    expect(screen.getByTestId('container-bg').textContent).toBe('#ffffff');
    expect(screen.getByTestId('mode').textContent).toBe('light');
  });

  it('re-themes in place when the shell toggles the theme', async () => {
    setHostTheme('light');
    render(
      <ZeppelinThemeProvider>
        <Probe />
      </ZeppelinThemeProvider>
    );
    expect(screen.getByTestId('container-bg').textContent).toBe('#ffffff');

    await act(async () => {
      setHostTheme('dark');
    });

    expect(screen.getByTestId('container-bg').textContent).toBe('#141414');
  });

  it('keeps surface tokens while switching algorithms', () => {
    setHostTheme('dark');
    render(
      <ZeppelinThemeProvider token={{ fontFamily: 'Consolas' }}>
        <Probe />
      </ZeppelinThemeProvider>
    );

    expect(screen.getByTestId('font').textContent).toBe('Consolas');
    expect(screen.getByTestId('container-bg').textContent).toBe('#141414');
  });
});
