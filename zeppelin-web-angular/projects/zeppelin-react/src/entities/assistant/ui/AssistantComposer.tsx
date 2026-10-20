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

import { ArrowUpOutlined, LockOutlined } from '@ant-design/icons';
import { Button, Input } from 'antd';
import { useId, type FormEvent, type KeyboardEvent } from 'react';
import * as styles from './AssistantComposer.css';

export interface AssistantComposerProps {
  value: string;
  onChange: (value: string) => void;
  /** Receives the question without surrounding whitespace. Clearing the input afterwards is up to the parent. */
  onSubmit: (question: string) => void;
  /** The question cannot be sent here, for example in a conversation started by another user. */
  readOnly?: boolean;
  readOnlyReason?: string;
  /** A question is on its way: the input keeps its text but cannot be changed or sent again. */
  sending?: boolean;
}

export const AssistantComposer = ({
  value,
  onChange,
  onSubmit,
  readOnly = false,
  readOnlyReason = "You can't send messages to this conversation.",
  sending = false
}: AssistantComposerProps) => {
  const hintId = useId();
  const question = value.trim();
  // The server rejects a blank question, and it accepts one question per conversation at a time.
  const canSubmit = question !== '' && !readOnly && !sending;

  const submit = () => {
    if (canSubmit) onSubmit(question);
  };
  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    submit();
  };
  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter that confirms an IME composition (such as Korean or Japanese input) is not a request to send.
    // Safari fires compositionend before that Enter, so it arrives with isComposing false and keyCode 229.
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing || event.keyCode === 229) return;
    event.preventDefault();
    submit();
  };

  return (
    <form className={styles.composer} aria-label="Ask the assistant" onSubmit={handleSubmit}>
      {readOnly && (
        <p className={styles.note} role="note">
          <LockOutlined aria-hidden="true" />
          <span>{readOnlyReason}</span>
        </p>
      )}
      <div className={styles.field} data-disabled={readOnly || sending || undefined}>
        <Input.TextArea
          className={styles.input}
          variant="borderless"
          autoSize={{ minRows: 2, maxRows: 8 }}
          aria-label="Question"
          aria-describedby={hintId}
          placeholder="Ask about this notebook"
          value={value}
          disabled={readOnly}
          readOnly={sending}
          onChange={event => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        <div className={styles.footer}>
          <small id={hintId} className={styles.hint}>
            Shift + Enter for a new line
          </small>
          <Button type="primary" htmlType="submit" icon={<ArrowUpOutlined />} loading={sending} disabled={!canSubmit}>
            Send
          </Button>
        </div>
      </div>
    </form>
  );
};
