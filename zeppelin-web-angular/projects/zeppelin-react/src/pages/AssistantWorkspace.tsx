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

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { createRoot, Root } from 'react-dom/client';
import { CloseOutlined } from '@ant-design/icons';
import { theme } from 'antd';
import type { AssistantShellProps } from '@zeppelin/sdk';
import { ReactErrorBoundary } from '@/components';
import { ZeppelinThemeProvider } from '@/theme';
import { assistantThemeStyles } from '@/shared/ui/assistant-theme';
import { scope } from '@/shared/ui/assistant-theme/AssistantScope.css';
import { usePanelResize } from './assistant-workspace/lib/usePanelResize';
import * as styles from './AssistantWorkspace.css';

export type AssistantWorkspaceProps = AssistantShellProps;

export const AssistantWorkspace = ({
  noteId,
  slots,
  panelWidth,
  onPanelWidthChange,
  onPanelVisibilityChange,
  subscribePanelClose
}: AssistantWorkspaceProps) => {
  const { token } = theme.useToken();
  const themeStyles = assistantThemeStyles(token);
  const [visible, setVisible] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  const navigation = slots.find(slot => slot.kind === 'navigation');
  const panel = slots.find(slot => slot.kind === 'panel');
  const { handleProps } = usePanelResize(panel?.element, panelWidth, onPanelWidthChange);

  useEffect(() => onPanelVisibilityChange(visible), [onPanelVisibilityChange, visible]);
  useEffect(() => () => onPanelVisibilityChange(false), [onPanelVisibilityChange]);
  useEffect(() => subscribePanelClose(() => setVisible(false)), [subscribePanelClose]);
  useEffect(() => setVisible(false), [noteId]);
  useLayoutEffect(() => {
    const element = panel?.element;
    if (!element || !visible) return;
    const measure = () =>
      element.style.setProperty('--assistant-panel-top', `${Math.max(0, element.getBoundingClientRect().top)}px`);
    measure();
    window.addEventListener('scroll', measure, true);
    window.addEventListener('resize', measure);
    return () => {
      window.removeEventListener('scroll', measure, true);
      window.removeEventListener('resize', measure);
      element.style.removeProperty('--assistant-panel-top');
    };
  }, [panel?.element, visible]);

  const close = () => {
    setVisible(false);
    button.current?.focus();
  };

  return (
    <>
      {navigation &&
        createPortal(
          <button
            ref={button}
            type="button"
            className={`${scope} ${styles.nav}`}
            style={themeStyles}
            aria-label="Toggle AI Assistant"
            aria-pressed={visible}
            onClick={() => setVisible(open => !open)}
          >
            <span className={styles.navIcon} aria-hidden="true">
              AI
            </span>
          </button>,
          navigation.element
        )}
      {visible &&
        panel &&
        createPortal(
          <aside
            className={`${scope} ${styles.panel}`}
            aria-label="AI Assistant workspace"
            style={themeStyles}
            onKeyDown={event => {
              if (event.key === 'Escape') close();
            }}
          >
            <header className={styles.header}>
              <h2 className={styles.title}>AI Assistant</h2>
              <button type="button" className={styles.close} aria-label="Close AI Assistant" onClick={close}>
                <CloseOutlined aria-hidden="true" />
              </button>
            </header>
            <div className={styles.resize} {...handleProps} />
          </aside>,
          panel.element
        )}
    </>
  );
};

export const mount = (element: HTMLElement, initialProps: AssistantWorkspaceProps) => {
  const root: Root = createRoot(element);
  const renderWith = (props: AssistantWorkspaceProps) => {
    root.render(
      <ReactErrorBoundary onError={props.onError}>
        <ZeppelinThemeProvider>
          <AssistantWorkspace {...props} />
        </ZeppelinThemeProvider>
      </ReactErrorBoundary>
    );
  };

  renderWith(initialProps);
  return {
    update: renderWith,
    unmount: () => root.unmount()
  };
};
