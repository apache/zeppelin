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

import { type ReactNode, useEffect, useEffectEvent, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import CloseOutlined from '@ant-design/icons/CloseOutlined';
import { theme } from 'antd';
import type { AssistantShellProps } from '@zeppelin/sdk';
import { assistantThemeStyles } from '@/shared/ui/assistant-theme/AssistantScope';
import {
  createAssistantConversationApi,
  socketRun,
  type AssistantAuthErrorHandler
} from '@/entities/assistant/model/assistantTransport';
import { scope } from '@/shared/ui/assistant-theme/AssistantScope.css';
import { usePanelResize } from '../lib/usePanelResize';
import { AssistantPanelModel } from '../model/assistantPanelModel';
import { useAssistantPanelSnapshot } from '../model/useAssistantPanel';
import { AssistantConversationPanel } from './AssistantConversationPanel';
import { createNotebookAnswerRenderer } from './createNotebookAnswerRenderer';
import * as styles from '../../AssistantWorkspace.css';

interface PanelSession {
  attachView: () => () => void;
  model: AssistantPanelModel;
}

const createPanelSession = (noteId: string, connection: AssistantShellProps['connection']): PanelSession => {
  let activeViewAuthHandler: AssistantAuthErrorHandler | undefined;
  return {
    model: new AssistantPanelModel({
      api: createAssistantConversationApi(connection.apiBase, noteId, () => activeViewAuthHandler),
      run: (conversationId, content, signal) => socketRun(connection.socket, { noteId, conversationId, content }, signal)
    }),
    attachView: () => {
      const onAuthError: AssistantAuthErrorHandler = (status, location) => {
        if (activeViewAuthHandler === onAuthError) connection.onAuthError(status, location);
      };
      activeViewAuthHandler = onAuthError;
      return () => {
        if (activeViewAuthHandler === onAuthError) activeViewAuthHandler = undefined;
      };
    }
  };
};

const PanelSessionOwner = ({ session, children }: { session: PanelSession; children: ReactNode }) => {
  useEffect(() => () => session.model.dispose(), [session]);
  return children;
};

const OpenConversationPanel = ({
  session,
  paragraphs,
  onOpenParagraph
}: Pick<AssistantShellProps, 'paragraphs' | 'onOpenParagraph'> & { session: PanelSession }) => {
  const panel = useAssistantPanelSnapshot(session.model);

  useLayoutEffect(() => session.attachView(), [session]);
  useEffect(() => {
    session.model.start();
    session.model.reopen();
  }, [session]);

  const renderAnswer = useMemo(
    () => createNotebookAnswerRenderer(paragraphs, onOpenParagraph),
    [paragraphs, onOpenParagraph]
  );

  return <AssistantConversationPanel panel={panel} scope={session.model} renderAnswer={renderAnswer} />;
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
  const [sidebar, setSidebar] = useState<{ session: PanelSession; open: boolean }>();
  const session = sidebar?.session;
  const visible = sidebar?.open ?? false;
  const button = useRef<HTMLButtonElement>(null);
  const navigation = slots.find(slot => slot.kind === 'navigation');
  const panel = slots.find(slot => slot.kind === 'panel');
  const { handleProps } = usePanelResize(panel?.element, panelWidth, onPanelWidthChange);
  const notifyUnmount = useEffectEvent(() => onPanelVisibilityChange(false));
  useEffect(() => () => notifyUnmount(), []);

  const openPanel = () => {
    setSidebar({ open: true, session: session ?? createPanelSession(noteId, connection) });
    onPanelVisibilityChange(true);
  };
  const hidePanel = () => {
    if (!sidebar?.open) return;
    const retain = sidebar.session.model.close();
    setSidebar(retain ? { session: sidebar.session, open: false } : undefined);
    onPanelVisibilityChange(false);
  };
  const hideFromHost = useEffectEvent(hidePanel);
  useEffect(() => subscribePanelClose(() => hideFromHost()), [subscribePanelClose]);

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
    hidePanel();
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
              onClick={visible ? hidePanel : openPanel}
            >
              <span className={styles.navIcon} aria-hidden="true">
                AI
              </span>
            </button>,
            navigation.element
          )
        : null}
      {session && (
        <PanelSessionOwner session={session}>
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
                  <OpenConversationPanel session={session} paragraphs={paragraphs} onOpenParagraph={onOpenParagraph} />
                  <div className={styles.resize} {...handleProps} />
                </aside>,
                panel.element
              )
            : null}
        </PanelSessionOwner>
      )}
    </>
  );
};
