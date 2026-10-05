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

import { createContext, ReactNode, useContext } from 'react';
import { ConfigProvider, theme as antdTheme, ThemeConfig } from 'antd';
import { HostThemeMode, useHostTheme } from './hostTheme';

const HostThemeContext = createContext<HostThemeMode>('light');

/** Resolved host theme for code that draws outside antd, such as canvas charts. */
export const useHostThemeMode = (): HostThemeMode => useContext(HostThemeContext);

export interface ZeppelinThemeProviderProps {
  children: ReactNode;
  /** Extra tokens for a single surface, e.g. a monospace result font. */
  token?: ThemeConfig['token'];
}

/**
 * Every exposed module should render inside this provider. Without it antd
 * builds its styles from the default (light) algorithm, and the remote looks
 * dark only for as long as the shell's global `.ant-*` rules happen to cover
 * the components in use.
 */
export const ZeppelinThemeProvider = ({ children, token }: ZeppelinThemeProviderProps) => {
  const mode = useHostTheme();

  return (
    <ConfigProvider
      theme={{
        algorithm: mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token
      }}
    >
      <HostThemeContext.Provider value={mode}>{children}</HostThemeContext.Provider>
    </ConfigProvider>
  );
};
