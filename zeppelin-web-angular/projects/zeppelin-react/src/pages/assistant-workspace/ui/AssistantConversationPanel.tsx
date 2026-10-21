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

import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { AssistantAnswerError } from '@/entities/assistant/ui/AssistantAnswerError';
import { AssistantComposer } from '@/entities/assistant/ui/AssistantComposer';
import { ConversationHeader } from '@/entities/assistant/ui/ConversationHeader';
import { ConversationList } from '@/entities/assistant/ui/ConversationList';
import { useDeleteConfirmation } from '../lib/useDeleteConfirmation';
import type { AssistantDataSource } from '../model/assistantDataSource';
import { useAssistantPanel } from '../model/useAssistantPanel';
import { ConversationMessages } from './ConversationMessages';
import { DeleteConversationDialog } from './DeleteConversationDialog';
import * as styles from './AssistantConversationPanel.css';

export interface AssistantConversationPanelProps {
  source: AssistantDataSource;
  now?: number;
}

const ignoreSearchChange = () => undefined;

export const AssistantConversationPanel = ({ source, now }: AssistantConversationPanelProps) => {
  const panel = useAssistantPanel(source);
  const { entry } = panel;

  const [listOpen, setListOpen] = useState(false);
  const listTrigger = useRef<HTMLButtonElement>(null);
  const back = useRef<HTMLButtonElement>(null);
  const navigation = useRef({ scope: source, open: false });

  const fallbackFocus = useCallback(() => (listOpen ? back.current : listTrigger.current), [listOpen]);
  const confirmation = useDeleteConfirmation({
    scope: source,
    conversations: panel.conversations,
    listLoading: panel.listLoading,
    deletingConversationIds: panel.deletingConversationIds,
    error: panel.listError,
    onDelete: panel.deleteConversation,
    fallbackFocus
  });

  useLayoutEffect(() => {
    if (navigation.current.scope !== source) {
      navigation.current = { scope: source, open: false };
      setListOpen(false);
      return;
    }

    if (confirmation.target) return;

    const wasOpen = navigation.current.open;
    navigation.current.open = listOpen;
    if (listOpen && !wasOpen) back.current?.focus();
    else if (!listOpen && wasOpen) listTrigger.current?.focus();
  }, [source, listOpen, confirmation.target]);

  const closeList = () => setListOpen(false);

  const createDraft = () => {
    panel.newConversation();
    closeList();
  };

  const busy = entry.phase === 'creating' || entry.phase === 'pending' || entry.phase === 'running';

  const recoveryActions = {
    send: () => {
      void panel.send();
    },
    reload: () => {
      void panel.reload();
    }
  };
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
      <div className={styles.content} inert={!!confirmation.target}>
        <div className={`${styles.conversation} ${listOpen ? styles.hidden : ''}`} hidden={listOpen}>
          <ConversationHeader
            conversation={entry.conversation}
            onCreate={createDraft}
            newDisabled={panel.creatingConversation}
            onDelete={confirmation.request}
            onOpenList={() => setListOpen(true)}
            listTriggerRef={listTrigger}
          />

          <div className={styles.statusArea}>
            {panel.listError && (
              <p role="alert" className={styles.warning}>
                {panel.listError}
              </p>
            )}
            {entry.uncertainty && (
              <p role="alert" className={styles.warning}>
                {entry.uncertainty}
              </p>
            )}
            {entry.failure && (
              <AssistantAnswerError kind={entry.failure.kind} message={entry.failure.message} onRetry={retry} />
            )}
            {(entry.phase === 'creating' || entry.phase === 'pending') && (
              <p className={styles.note} role="status">
                {entry.phase === 'creating' ? 'Creating conversation…' : 'Sending question…'}
              </p>
            )}
          </div>

          <ConversationMessages
            key={entry.key}
            scope={source}
            entry={entry}
            visible={!listOpen}
            loadEarlier={panel.loadEarlier}
            onRetry={answerRetry}
          />

          <div className={styles.composer}>
            <AssistantComposer
              value={entry.draft}
              onChange={panel.setDraft}
              onSubmit={question => {
                void panel.send(question);
              }}
              readOnly={entry.conversation?.canSendMessage === false}
              sending={busy || entry.deleting}
            />
          </div>
        </div>

        {listOpen && (
          <div className={styles.listView}>
            <div className={styles.listNavigation}>
              <button ref={back} type="button" className={styles.action} onClick={closeList}>
                Back
              </button>
            </div>
            {panel.listLoading && (
              <p role="status" className={styles.note}>
                Loading conversations…
              </p>
            )}
            {panel.listError && (
              <p role="alert" className={styles.warning}>
                {panel.listError}
              </p>
            )}
            <ConversationList
              conversations={panel.conversations}
              selectedId={entry.conversation?.id}
              onSelect={id => {
                panel.select(id);
                closeList();
              }}
              onCreate={createDraft}
              newDisabled={panel.creatingConversation}
              onDelete={confirmation.request}
              onSearchTermChange={ignoreSearchChange}
              now={now}
            />
          </div>
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
