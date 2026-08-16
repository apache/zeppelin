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
import { afterEach, describe, expect, it } from 'vitest';
import { HostThemeMode, readHostTheme, useHostTheme } from './hostTheme';

const Probe = () => <span data-testid="mode">{useHostTheme()}</span>;

const setHostTheme = (mode: HostThemeMode) => {
  document.documentElement.setAttribute('data-theme', mode);
  document.documentElement.classList.remove('light', 'dark');
  document.documentElement.classList.add(mode);
};

const stubMatchMedia = (matches: boolean) => {
  const listeners = new Set<() => void>();
  const mql = {
    matches,
    addEventListener: (_: string, cb: () => void) => listeners.add(cb),
    removeEventListener: (_: string, cb: () => void) => listeners.delete(cb)
  };
  (window as unknown as { matchMedia?: unknown }).matchMedia = () => mql;
  return {
    set: (next: boolean) => {
      mql.matches = next;
      listeners.forEach(cb => cb());
    },
    listenerCount: () => listeners.size
  };
};

describe('readHostTheme', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.classList.remove('light', 'dark');
    delete (window as unknown as { matchMedia?: unknown }).matchMedia;
  });

  it('reads the theme the shell writes to the document root', () => {
    setHostTheme('dark');
    expect(readHostTheme()).toBe('dark');

    setHostTheme('light');
    expect(readHostTheme()).toBe('light');
  });

  it('falls back to the root class when the attribute is missing', () => {
    document.documentElement.classList.add('dark');
    expect(readHostTheme()).toBe('dark');
  });

  it('falls back to the OS preference when the shell declares nothing', () => {
    stubMatchMedia(true);
    expect(readHostTheme()).toBe('dark');
  });

  it('defaults to light when neither the shell nor matchMedia is available', () => {
    expect(readHostTheme()).toBe('light');
  });
});

describe('useHostTheme', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.classList.remove('light', 'dark');
    delete (window as unknown as { matchMedia?: unknown }).matchMedia;
  });

  it('starts from the declared theme', () => {
    setHostTheme('dark');
    render(<Probe />);

    expect(screen.getByTestId('mode').textContent).toBe('dark');
  });

  it('follows the shell when the user toggles the theme while mounted', async () => {
    setHostTheme('light');
    render(<Probe />);
    expect(screen.getByTestId('mode').textContent).toBe('light');

    await act(async () => {
      setHostTheme('dark');
    });

    expect(screen.getByTestId('mode').textContent).toBe('dark');
  });

  it('follows the OS only while the shell has declared nothing', () => {
    const media = stubMatchMedia(false);
    render(<Probe />);
    expect(screen.getByTestId('mode').textContent).toBe('light');
    expect(media.listenerCount()).toBe(1);

    act(() => {
      media.set(true);
    });

    expect(screen.getByTestId('mode').textContent).toBe('dark');
  });

  it('does not subscribe to the OS when the shell declares a theme', () => {
    const media = stubMatchMedia(true);
    setHostTheme('light');
    render(<Probe />);

    // The shell already resolved 'system' for us, so a second source would
    // let the OS override an explicit light/dark choice.
    expect(screen.getByTestId('mode').textContent).toBe('light');
    expect(media.listenerCount()).toBe(0);
  });
});
