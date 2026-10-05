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

import { useEffect, useState } from 'react';

export type HostThemeMode = 'light' | 'dark';

/**
 * The Angular shell's ThemeService resolves 'system' for us and writes the
 * result to the document root as `data-theme` plus a `dark`/`light` class.
 * Reading that is what keeps the remote in step with the host without the
 * host having to thread a prop through every mount point.
 */
export const readHostTheme = (): HostThemeMode => {
  const root = document.documentElement;
  const declared = root.getAttribute('data-theme');
  if (declared === 'dark' || declared === 'light') {
    return declared;
  }
  if (root.classList.contains('dark')) {
    return 'dark';
  }
  if (root.classList.contains('light')) {
    return 'light';
  }

  // Standalone dev server (port 3001) has no shell, so fall back to the OS
  // preference the shell would have resolved itself.
  if (typeof window.matchMedia === 'function') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  return 'light';
};

const hostDeclaresTheme = (): boolean => {
  const root = document.documentElement;
  return root.hasAttribute('data-theme') || root.classList.contains('dark') || root.classList.contains('light');
};

/** Resolved host theme, kept up to date while mounted. */
export const useHostTheme = (): HostThemeMode => {
  const [mode, setMode] = useState<HostThemeMode>(readHostTheme);

  useEffect(() => {
    // The shell can apply its theme after the remote mounts, so re-read once
    // the subscription is in place rather than trusting the initial render.
    setMode(readHostTheme());
    const sync = () => setMode(readHostTheme());

    const observer = new MutationObserver(sync);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] });

    // Only follow the OS while the shell has not declared a theme; once it
    // has, its value already accounts for the 'system' setting.
    let media: MediaQueryList | undefined;
    if (!hostDeclaresTheme() && typeof window.matchMedia === 'function') {
      media = window.matchMedia('(prefers-color-scheme: dark)');
      media.addEventListener('change', sync);
    }

    return () => {
      observer.disconnect();
      media?.removeEventListener('change', sync);
    };
  }, []);

  return mode;
};
