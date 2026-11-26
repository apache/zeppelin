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

import { Component, createRef, type RefObject, useCallback, useLayoutEffect, useRef, useState } from 'react';
import { AssistantAnswerError } from '@/entities/assistant/ui/AssistantAnswerError';
import { AssistantComposer } from '@/entities/assistant/ui/AssistantComposer';
import { ConversationHeader } from '@/entities/assistant/ui/ConversationHeader';
import { ConversationList, type ConversationListProps } from '@/entities/assistant/ui/ConversationList';
import type { AssistantContentRenderer } from '@/entities/assistant/ui/answerView';
import { action, note, warning } from '@/shared/ui/assistant-theme/AssistantScope.css';
import { useDeleteConfirmation } from '../lib/useDeleteConfirmation';
import type { AssistantPanel } from '../model/assistantPanelState';
import { ConversationMessages } from './ConversationMessages';
import { DeleteConversationDialog } from './DeleteConversationDialog';
import * as styles from './AssistantConversationPanel.css';

export interface AssistantConversationPanelProps {
  panel: AssistantPanel;
  scope: object;
  now?: number;
  renderAnswer?: AssistantContentRenderer;
}

interface ConversationListViewProps {
  scope: object;
  inert: boolean;
  backRef: RefObject<HTMLButtonElement | null>;
  onBack: () => void;
  list: ConversationListProps;
}

class ConversationListView extends Component<ConversationListViewProps> {
  private readonly root = createRef<HTMLDivElement>();

  // A layout effect would read focus after React removes the row.
  getSnapshotBeforeUpdate(previous: ConversationListViewProps): Element | null {
    const { scope, inert, list } = this.props;
    if (
      inert ||
      previous.scope !== scope ||
      previous.list.listState.conversations === list.listState.conversations
    ) {
      return null;
    }

    const root = this.root.current;
    if (!root) return null;

    const row = root.ownerDocument.activeElement?.closest('li');
    return row && root.contains(row) ? row : null;
  }

  componentDidUpdate(_previous: ConversationListViewProps, _state: unknown, row: Element | null): void {
    if (!row || row.isConnected || row.ownerDocument.activeElement !== row.ownerDocument.body) return;

    this.props.backRef.current?.focus();
  }

  render() {
    const { backRef, onBack, list } = this.props;
    return (
      <div ref={this.root} className={styles.listView}>
        <div className={styles.listNavigation}>
          <button ref={backRef} type="button" className={action} onClick={onBack}>
            Back
          </button>
        </div>
        <ConversationList {...list} />
      </div>
    );
  }
}

const ignoreSearchChange = () => undefined;

export const AssistantConversationPanel = ({ panel, scope, now, renderAnswer }: AssistantConversationPanelProps) => {
  const { conversationList, entry, send: sendQuestion, reload: reloadHistory, retryList } = panel;

  const [listOpen, setListOpen] = useState(false);
  const listTrigger = useRef<HTMLButtonElement>(null);
  const back = useRef<HTMLButtonElement>(null);
  const navigation = useRef({ scope, open: false });

  const fallbackFocus = useCallback(() => (listOpen ? back.current : listTrigger.current), [listOpen]);
  const confirmation = useDeleteConfirmation({
    scope,
    conversations: conversationList.conversations,
    listLoading: conversationList.loading,
    deletingConversationIds: panel.deletingConversationIds,
    onDelete: panel.deleteConversation,
    fallbackFocus
  });
  const contentInert = !!confirmation.target;

  useLayoutEffect(() => {
    if (navigation.current.scope !== scope) {
      navigation.current = { scope, open: false };
      setListOpen(false);
      return;
    }

    if (confirmation.target) return;

    const wasOpen = navigation.current.open;
    navigation.current.open = listOpen;
    if (listOpen && !wasOpen) back.current?.focus();
    else if (!listOpen && wasOpen) listTrigger.current?.focus();
  }, [scope, listOpen, confirmation.target]);

  const openList = () => {
    panel.openList();
    setListOpen(true);
  };

  const closeList = () => setListOpen(false);

  const createDraft = () => {
    panel.newConversation();
    closeList();
  };

  const busy = entry.phase === 'creating' || entry.phase === 'pending' || entry.phase === 'running';

  const retrySend = useCallback(() => {
    void sendQuestion();
  }, [sendQuestion]);
  const retryReload = useCallback(() => {
    void reloadHistory();
  }, [reloadHistory]);

  const recoveryActions = { send: retrySend, reload: retryReload };
  const retry = entry.failure?.recovery ? recoveryActions[entry.failure.recovery] : undefined;
  const answerRetry = entry.answerRecovery ? recoveryActions[entry.answerRecovery] : undefined;

  return (
    <section
      className={styles.panel}
      aria-label="Assistant conversation panel"
      onKeyDown={event => {
        if (listOpen && event.key === 'Escape') {
          event.preventDefault();
          event.stopPropagation();
          closeList();
        }
      }}
    >
      <div className={styles.content} inert={contentInert}>
        <div className={`${styles.conversation} ${listOpen ? styles.hidden : ''}`} hidden={listOpen}>
          <ConversationHeader
            conversation={entry.conversation}
            onCreate={createDraft}
            newDisabled={panel.creatingConversation}
            onDelete={confirmation.request}
            onOpenList={openList}
            listTriggerRef={listTrigger}
          />

          <div className={styles.statusArea}>
            {conversationList.error && (
              <p role="alert" className={warning}>
                {conversationList.error}
              </p>
            )}
            {entry.uncertainty && (
              <p role="alert" className={warning}>
                {entry.uncertainty}
              </p>
            )}
            {entry.failure && (
              <AssistantAnswerError kind={entry.failure.kind} message={entry.failure.message} onRetry={retry} />
            )}
            {(entry.phase === 'creating' || entry.phase === 'pending') && (
              <p className={note} role="status">
                {entry.phase === 'creating' ? 'Creating conversation…' : 'Sending question…'}
              </p>
            )}
          </div>

          <ConversationMessages
            key={entry.key}
            scope={scope}
            entry={entry}
            visible={!listOpen}
            loadEarlier={panel.loadEarlier}
            onRetry={answerRetry}
            renderAnswer={renderAnswer}
          />

          <div className={styles.composer}>
            <AssistantComposer
              value={entry.draft}
              onChange={panel.setDraft}
              onSubmit={question => {
                void panel.send(question);
              }}
              readOnly={entry.conversation?.canSendMessage === false}
              submitDisabled={!!entry.conversation && !entry.loaded}
              sending={busy || entry.deleting}
            />
          </div>
        </div>

        {listOpen && (
          <ConversationListView
            scope={scope}
            inert={contentInert}
            backRef={back}
            onBack={closeList}
            list={{
              listState: conversationList,
              onRetry: () => void retryList(),
              selectedId: entry.conversation?.id,
              onSelect: id => {
                panel.select(id);
                closeList();
              },
              onCreate: createDraft,
              newDisabled: panel.creatingConversation,
              onDelete: confirmation.request,
              onSearchTermChange: ignoreSearchChange,
              now
            }}
          />
        )}
      </div>

      {confirmation.target && (
        <DeleteConversationDialog
          title={confirmation.target.title}
          deleting={confirmation.deleting}
          error={confirmation.error}
          onCancel={confirmation.cancel}
          onConfirm={() => {
            void confirmation.confirm();
          }}
        />
      )}
    </section>
  );
};
