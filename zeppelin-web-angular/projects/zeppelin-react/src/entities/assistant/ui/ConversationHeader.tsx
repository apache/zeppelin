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

import * as styles from './ConversationHeader.css';

import { DeleteOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { visuallyHidden } from '@/shared/ui/assistant-theme/AssistantScope.css';
import type { AssistantConversation } from '../model/assistantContract';

export interface ConversationHeaderProps {
  conversation?: AssistantConversation;
  onCreate: () => void;
  onDelete: (conversationId: string) => void;
}

export const ConversationHeader = ({ conversation, onCreate, onDelete }: ConversationHeaderProps) => {
  const title = conversation ? conversation.title : 'No conversation selected';
  const readOnly = conversation?.canSendMessage === false;
  return (
    <header className={styles.header}>
      {readOnly && (
        <>
          <LockOutlined className={styles.lock} aria-hidden="true" />
          <span className={visuallyHidden}>Read-only</span>
        </>
      )}
      <span className={styles.title}>{title}</span>
      <Button size="small" type="text" icon={<PlusOutlined />} aria-label="New conversation" onClick={onCreate} />
      {conversation && !readOnly && (
        <Button
          size="small"
          type="text"
          icon={<DeleteOutlined />}
          aria-label={`Delete ${conversation.title}`}
          onClick={() => onDelete(conversation.id)}
        />
      )}
    </header>
  );
};
