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

import { useEffect, useEffectEvent, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import CloseOutlined from '@ant-design/icons/CloseOutlined';
import { theme } from 'antd';
import type { AssistantShellProps } from '@zeppelin/sdk';
import { assistantThemeStyles } from '@/shared/ui/assistant-theme/AssistantScope';
import { createAssistantConversationApi, socketRun } from '@/entities/assistant/model/assistantTransport';
import { scope } from '@/shared/ui/assistant-theme/AssistantScope.css';
import { usePanelResize } from '../lib/usePanelResize';
import type { AssistantDataSource } from '../model/assistantDataSource';
import { AssistantConversationPanel } from './AssistantConversationPanel';
import { createNotebookAnswerRenderer } from './createNotebookAnswerRenderer';
import * as styles from '../../AssistantWorkspace.css';

const OpenConversationPanel = ({
  noteId,
  connection,
  paragraphs,
  onOpenParagraph
}: Pick<AssistantShellProps, 'noteId' | 'connection' | 'paragraphs' | 'onOpenParagraph'>) => {
  const active = useRef(false);
  const [dataSource] = useState<AssistantDataSource>(() => ({
    api: createAssistantConversationApi(connection.apiBase, noteId, (status, location) => {
      if (active.current) {
        connection.onAuthError(status, location);
      }
    }),
    run: (conversationId, content, signal) => socketRun(connection.socket, { noteId, conversationId, content }, signal)
  }));

  // Invalidate authentication callbacks at unmount, before passive request cleanup runs.
  useLayoutEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);

  const renderAnswer = useMemo(
    () => createNotebookAnswerRenderer(paragraphs, onOpenParagraph),
    [paragraphs, onOpenParagraph]
  );

  return <AssistantConversationPanel source={dataSource} renderAnswer={renderAnswer} />;
};

export const AssistantWorkspaceShell = ({
  noteId,
  connection,
  paragraphs,
  onOpenParagraph,
  slots,
  panelWidth,
  onPanelWidthChange,
  onPanelVisibilityChange,
  subscribePanelClose
}: AssistantShellProps) => {
  const { token } = theme.useToken();
  const themeStyles = assistantThemeStyles(token);
  const [visible, setVisible] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  const navigation = slots.find(slot => slot.kind === 'navigation');
  const panel = slots.find(slot => slot.kind === 'panel');
  const { handleProps } = usePanelResize(panel?.element, panelWidth, onPanelWidthChange);
  const notifyVisibility = useEffectEvent(onPanelVisibilityChange);

  useEffect(() => notifyVisibility(visible), [visible]);
  useEffect(() => () => notifyVisibility(false), []);
  useEffect(() => subscribePanelClose(() => setVisible(false)), [subscribePanelClose]);

  useLayoutEffect(() => {
    const element = panel?.element;
    if (!element || !visible) return;

    const measure = () => {
      element.style.setProperty('--assistant-panel-top', `${Math.max(0, element.getBoundingClientRect().top)}px`);
    };
    measure();

    window.addEventListener('scroll', measure, { capture: true, passive: true });
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
      {navigation
        ? createPortal(
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
          )
        : null}
      {visible && panel
        ? createPortal(
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
              <OpenConversationPanel
                noteId={noteId}
                connection={connection}
                paragraphs={paragraphs}
                onOpenParagraph={onOpenParagraph}
              />
              <div className={styles.resize} {...handleProps} />
            </aside>,
            panel.element
          )
        : null}
    </>
  );
};
