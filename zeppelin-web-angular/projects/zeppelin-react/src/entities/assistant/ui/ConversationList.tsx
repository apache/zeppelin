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

import { useState } from 'react';
import { DeleteOutlined, LockOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Input } from 'antd';
import { isSameDay } from 'date-fns';
import type { AssistantConversation } from '../model/assistantContract';
import { conversationTitle } from './conversationTitle';
import * as styles from './ConversationList.css';

const MINUTE = 60_000;
const relativeTime = new Intl.RelativeTimeFormat('en', { numeric: 'auto', style: 'short' });

const formatRelative = (iso: string, now: number): string => {
  const minutes = Math.round((Date.parse(iso) - now) / MINUTE);
  if (minutes > -1) return 'now';
  if (minutes > -60) return relativeTime.format(minutes, 'minute');
  return relativeTime.format(Math.round(minutes / 60), 'hour');
};

const matchesSearch = (conversation: AssistantConversation, needle: string) =>
  conversationTitle(conversation).toLowerCase().includes(needle);

export interface ConversationListProps {
  conversations: AssistantConversation[];
  selectedId?: string;
  onSelect: (conversationId: string) => void;
  onCreate: () => void;
  onDelete: (conversationId: string) => void;
  onSearchTermChange: (term: string) => void;
  now?: number;
}

const ConversationRow = ({
  conversation,
  selected,
  showTime,
  now,
  onSelect,
  onDelete
}: {
  conversation: AssistantConversation;
  selected: boolean;
  showTime: boolean;
  now: number;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
}) => {
  const title = conversationTitle(conversation);
  const readOnly = conversation.canSendMessage === false;
  return (
    <li className={styles.row}>
      <button
        type="button"
        className={styles.rowButton}
        aria-current={selected}
        onClick={() => onSelect(conversation.id)}
      >
        <span className={styles.rowTitle}>{title}</span>
        {readOnly || showTime ? (
          <span className={styles.rowMeta}>
            {readOnly ? (
              <>
                <LockOutlined aria-hidden="true" />
                <span className={styles.visuallyHidden}>Read-only</span>
              </>
            ) : null}
            {showTime && conversation.updatedAt ? (
              <>
                <span className={styles.visuallyHidden}>, </span>
                <time dateTime={conversation.updatedAt}>{formatRelative(conversation.updatedAt, now)}</time>
              </>
            ) : null}
          </span>
        ) : null}
      </button>
      {!readOnly && (
        <Button
          size="small"
          type="text"
          icon={<DeleteOutlined />}
          aria-label={`Delete ${title}`}
          onClick={() => onDelete(conversation.id)}
        />
      )}
    </li>
  );
};

export const ConversationList = ({
  conversations,
  selectedId,
  onSelect,
  onCreate,
  onDelete,
  onSearchTermChange,
  now = Date.now()
}: ConversationListProps) => {
  const [query, setQuery] = useState('');
  const needle = query.trim().toLowerCase();
  const searching = needle.length > 0;
  const shown = searching ? conversations.filter(conversation => matchesSearch(conversation, needle)) : conversations;
  const today = shown.filter(conversation => conversation.updatedAt && isSameDay(conversation.updatedAt, now));
  const earlier = shown.filter(conversation => !today.includes(conversation));

  const handleSearchChange = (value: string) => {
    setQuery(value);
    onSearchTermChange(value);
  };

  const row = (conversation: AssistantConversation, showTime: boolean) => (
    <ConversationRow
      key={conversation.id}
      conversation={conversation}
      selected={conversation.id === selectedId}
      showTime={showTime}
      now={now}
      onSelect={onSelect}
      onDelete={onDelete}
    />
  );

  return (
    <section aria-label="Conversations" className={styles.list}>
      <div className={styles.header}>
        <h3 className={styles.title}>Conversations</h3>
        <Button size="small" type="text" icon={<PlusOutlined />} aria-label="New conversation" onClick={onCreate} />
      </div>
      <div className={styles.search}>
        <Input
          allowClear
          prefix={<SearchOutlined aria-hidden="true" />}
          placeholder="Search conversations"
          aria-label="Search conversations"
          value={query}
          onChange={event => handleSearchChange(event.target.value)}
        />
      </div>
      {shown.length === 0 ? (
        <p className={styles.empty} role="status">
          {searching ? 'No conversations match your search.' : 'No conversations yet.'}
        </p>
      ) : (
        <>
          {!searching && today.length > 0 && (
            <>
              <div className={styles.sectionLabel}>Today</div>
              <ul className={styles.items}>{today.map(conversation => row(conversation, true))}</ul>
            </>
          )}
          {!searching && earlier.length > 0 && (
            <>
              <div className={styles.sectionLabel}>Earlier</div>
              <ul className={styles.items}>{earlier.map(conversation => row(conversation, false))}</ul>
            </>
          )}
          {searching && <ul className={styles.items}>{shown.map(conversation => row(conversation, false))}</ul>}
        </>
      )}
    </section>
  );
};
