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

import { useId, useLayoutEffect, useRef, type KeyboardEvent } from 'react';
import * as styles from './AssistantConversationPanel.css';

interface DeleteConversationDialogProps {
  title: string;
  deleting: boolean;
  error?: string;
  onCancel: () => void;
  onConfirm: () => void;
}

export const DeleteConversationDialog = ({
  title,
  deleting,
  error,
  onCancel,
  onConfirm
}: DeleteConversationDialogProps) => {
  const titleId = useId();
  const descriptionId = useId();
  const dialog = useRef<HTMLDivElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);

  useLayoutEffect(() => {
    if (deleting) dialog.current?.focus();
    else cancel.current?.focus();
  }, [deleting]);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      onCancel();
    } else if (event.key === 'Tab') {
      const buttons = dialog.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
      if (!buttons?.length) {
        event.preventDefault();
        dialog.current?.focus();
        return;
      }

      const first = buttons[0];
      const last = buttons[buttons.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  };

  return (
    <div className={styles.confirmationBackdrop}>
      <div
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        aria-busy={deleting || undefined}
        tabIndex={-1}
        className={styles.confirmation}
        onKeyDown={handleKeyDown}
      >
        <h3 id={titleId} className={styles.confirmationTitle}>
          Delete conversation
        </h3>
        <p id={descriptionId} className={styles.confirmationDescription}>
          Delete “{title}”? This conversation and its messages will be removed.
        </p>
        {error && (
          <p role="alert" className={styles.warning}>
            {error}
          </p>
        )}
        {deleting && (
          <p role="status" className={styles.note}>
            Deleting conversation…
          </p>
        )}
        <div className={styles.confirmationActions}>
          <button ref={cancel} type="button" className={styles.action} onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className={styles.primaryAction} disabled={deleting} onClick={onConfirm}>
            Delete conversation
          </button>
        </div>
      </div>
    </div>
  );
};
