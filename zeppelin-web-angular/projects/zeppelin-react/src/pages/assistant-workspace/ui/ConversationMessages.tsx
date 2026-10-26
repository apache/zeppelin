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

import { useMemo } from 'react';
import { AssistantAnswer } from '@/entities/assistant/ui/AssistantAnswer';
import { AssistantMessageItem } from '@/entities/assistant/ui/AssistantMessageItem';
import { MarkdownAnswer } from '@/entities/assistant/ui/MarkdownAnswer';
import { useConversationScroll } from '../lib/useConversationScroll';
import type { AssistantPanelEntry } from '../model/assistantPanelState';
import * as styles from './AssistantConversationPanel.css';

const renderMarkdown = (content: string) => (
  <div data-conversation-markdown>
    <MarkdownAnswer content={content} />
  </div>
);

export const ConversationMessages = ({
  scope,
  entry,
  visible,
  loadEarlier,
  onRetry
}: {
  scope: object;
  entry: AssistantPanelEntry;
  visible: boolean;
  loadEarlier: () => Promise<boolean>;
  onRetry?: () => void;
}) => {
  const { messages, messageKeys, localAnswerViews, answer, answerKey } = entry;

  const messageItems = useMemo(() => {
    const items = messages.map(message => {
      const key = messageKeys.get(message.id) ?? message.id;
      return (
        <div key={key} data-message-key={key} className={styles.messageRow}>
          {message.role === 'user' ? (
            <AssistantMessageItem role="user" content={message.content} />
          ) : (
            <AssistantAnswer
              view={localAnswerViews.get(message.id) ?? { status: 'done', message }}
              renderContent={renderMarkdown}
            />
          )}
        </div>
      );
    });

    if (answer) {
      items.push(
        <div key={answerKey} data-message-key={answerKey} className={styles.messageRow}>
          <AssistantAnswer view={answer} renderContent={renderMarkdown} onRetry={onRetry} />
        </div>
      );
    }

    return items;
  }, [messages, messageKeys, localAnswerViews, answer, answerKey, onRetry]);

  const completedMessage = answer?.status === 'done' ? answer.message : undefined;
  const rowSegments = useMemo(() => {
    const segments = new Map<string, readonly { id: string }[]>();
    for (const message of messages) {
      if (message.role === 'assistant') segments.set(messageKeys.get(message.id) ?? message.id, message.segments);
    }

    if (answerKey && completedMessage) segments.set(answerKey, completedMessage.segments);
    return segments;
  }, [messages, messageKeys, answerKey, completedMessage]);

  const { viewport, content, onScroll } = useConversationScroll({
    scope,
    conversationKey: entry.key,
    rowSegments,
    visible,
    loadEarlier
  });

  return (
    <div
      ref={viewport}
      className={styles.messages}
      role="region"
      aria-label="Conversation messages"
      aria-busy={entry.loading || entry.loadingEarlier || undefined}
      tabIndex={0}
      onScroll={onScroll}
    >
      <div ref={content} className={styles.messageStack}>
        {entry.loadingEarlier && (
          <p className={styles.note} role="status">
            Loading earlier messages…
          </p>
        )}
        {entry.loading && messages.length === 0 && !answer && (
          <p className={styles.note} role="status">
            Loading conversation…
          </p>
        )}
        {!entry.loading && entry.loaded && messages.length === 0 && !answer && (
          <p className={styles.empty}>Ask a question about this notebook to start a conversation.</p>
        )}
        {messageItems}
      </div>
    </div>
  );
};
