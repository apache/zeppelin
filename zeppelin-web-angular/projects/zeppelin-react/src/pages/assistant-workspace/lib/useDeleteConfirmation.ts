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
import type { AssistantConversation } from '@/entities/assistant/model/assistantContract';
import { conversationTitle } from '@/entities/assistant/ui/conversationTitle';
import type { AssistantDeletionResult } from '../model/assistantPanelState';

interface DeleteConfirmationOptions {
  scope: object;
  conversations: readonly AssistantConversation[];
  listLoading: boolean;
  deletingConversationIds: readonly string[];
  onDelete: (id: string) => Promise<AssistantDeletionResult>;
  fallbackFocus: () => HTMLElement | null;
}

interface DeleteTarget {
  id: string;
  title: string;
}

type DeleteInteraction = {
  scope: object;
  trigger: HTMLElement | null;
} & (
  | { kind: 'open'; target: DeleteTarget; status: 'ready' | 'pending' }
  | { kind: 'open'; target: DeleteTarget; status: 'failed'; message: string }
  | { kind: 'closed' }
);

/** Closing the confirmation does not cancel an already requested deletion. */
export const useDeleteConfirmation = ({
  scope,
  conversations,
  listLoading,
  deletingConversationIds,
  onDelete,
  fallbackFocus
}: DeleteConfirmationOptions) => {
  const [interaction, setInteraction] = useState<DeleteInteraction | null>(null);
  const current = useRef<DeleteInteraction | null>(null);

  // Event handlers must see state changes before React commits.
  const transition = useCallback((next: DeleteInteraction | null) => {
    current.current = next;
    setInteraction(next);
  }, []);

  useLayoutEffect(() => {
    transition(null);

    return () => {
      current.current = null;
    };
  }, [scope, transition]);

  useLayoutEffect(() => {
    if (
      interaction?.scope !== scope ||
      interaction.kind !== 'open' ||
      listLoading ||
      conversations.some(conversation => conversation.id === interaction.target.id)
    )
      return;

    transition({ kind: 'closed', scope, trigger: interaction.trigger });
  }, [interaction, scope, listLoading, conversations, transition]);

  useLayoutEffect(() => {
    if (interaction?.scope !== scope || interaction.kind !== 'closed') return;

    transition(null);

    const target = interaction.trigger?.isConnected ? interaction.trigger : fallbackFocus();
    target?.focus();
  }, [interaction, scope, fallbackFocus, transition]);

  const request = (id: string) => {
    const conversation = conversations.find(item => item.id === id);
    if (!conversation || conversation.canSendMessage === false) return;

    transition({
      kind: 'open',
      scope,
      target: { id, title: conversationTitle(conversation) },
      trigger: document.activeElement instanceof HTMLElement ? document.activeElement : null,
      status: 'ready'
    });
  };

  const cancel = () => {
    const active = current.current;
    if (active?.scope !== scope || active.kind !== 'open') return;

    transition({ kind: 'closed', scope, trigger: active.trigger });
  };

  const confirm = async () => {
    const active = current.current;
    if (
      active?.scope !== scope ||
      active.kind !== 'open' ||
      active.status === 'pending' ||
      deletingConversationIds.includes(active.target.id)
    )
      return;

    const pending: DeleteInteraction = {
      kind: 'open',
      scope,
      trigger: active.trigger,
      target: active.target,
      status: 'pending'
    };
    transition(pending);

    const result = await onDelete(active.target.id);
    if (current.current !== pending) return;

    if (result.status === 'deleted') transition({ kind: 'closed', scope, trigger: pending.trigger });
    else if (result.status === 'failed') transition({ ...pending, status: 'failed', message: result.message });
    else transition({ ...pending, status: 'ready' });
  };

  const active = interaction?.scope === scope && interaction.kind === 'open' ? interaction : null;

  return {
    target: active?.target ?? null,
    deleting: !!active && (active.status === 'pending' || deletingConversationIds.includes(active.target.id)),
    error: active?.status === 'failed' ? active.message : undefined,
    request,
    cancel,
    confirm
  };
};
