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

import type { ReactNode } from 'react';
import type { AssistantMessage } from '../model/assistantContract';
import type { AssistantContentRenderer } from './answerView';
import * as styles from './AssistantMessageItem.css';

const renderPlainText: AssistantContentRenderer = content => <div className={styles.plainText}>{content}</div>;

export interface AssistantMessageItemProps {
  role: AssistantMessage['role'];
  content: string;
  /** The answer is still arriving: marks it busy and shows a caret after the text. */
  streaming?: boolean;
  renderContent?: AssistantContentRenderer;
  /** Shown under an assistant answer, such as the tools it used or an error. */
  children?: ReactNode;
}

export const AssistantMessageItem = ({
  role,
  content,
  streaming = false,
  renderContent = renderPlainText,
  children
}: AssistantMessageItemProps) => {
  if (role === 'user') {
    return (
      <article className={`${styles.message} ${styles.user}`} data-role="user" aria-label="Your question">
        <div className={styles.label}>You</div>
        <div className={`${styles.bubble} ${styles.plainText}`}>{content}</div>
      </article>
    );
  }
  return (
    <article
      className={`${styles.message} ${styles.assistant}`}
      data-role="assistant"
      aria-label="Assistant answer"
      aria-busy={streaming || undefined}
    >
      <header className={styles.label}>
        <span className={styles.badge} aria-hidden="true">
          AI
        </span>
        <span>Assistant</span>
      </header>
      {content && (
        <div className={styles.body}>
          {renderContent(content)}
          {streaming && <span className={styles.caret} aria-hidden="true" />}
        </div>
      )}
      {children}
    </article>
  );
};
