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
import { theme as antdTheme } from 'antd';
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

    // Light tokens would put a white container on the shell's dark page; today
    // that only goes unnoticed because the shell's global .ant-* rules cover it.
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
