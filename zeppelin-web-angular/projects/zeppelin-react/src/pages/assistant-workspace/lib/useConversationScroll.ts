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

import { useLayoutEffect, useRef } from 'react';

export type ConversationRowSegments = ReadonlyMap<string, readonly { id: string }[]>;

interface Anchor {
  row: HTMLElement;
  block: HTMLElement;
  text: string;
  blockIndex: number;
  blockCount: number;
  firstSegment?: string;
  blockOffset: number;
  rowOffset: number;
  neighbors: Array<{ row: HTMLElement; offset: number }>;
}

interface ScrollState {
  scope: object;
  key: string;
  follow: boolean;
  anchor?: Anchor;
  expectedTop?: number;
  interacting: boolean;
  top: number;
  height: number;
  width: number;
  contentHeight: number;
}

const markdownBlocks =
  '[data-conversation-markdown] p, [data-conversation-markdown] pre, ' +
  '[data-conversation-markdown] table, [data-conversation-markdown] li, ' +
  '[data-conversation-markdown] h1, [data-conversation-markdown] h2, ' +
  '[data-conversation-markdown] h3, [data-conversation-markdown] blockquote';

const captureAnchor = (
  viewport: HTMLElement,
  content: HTMLElement,
  rows: ConversationRowSegments
): Anchor | undefined => {
  const top = viewport.getBoundingClientRect().top;
  const bottom = top + viewport.clientHeight;

  for (const row of Array.from(content.querySelectorAll<HTMLElement>('[data-message-key]'))) {
    const rect = row.getBoundingClientRect();
    if (rect.bottom <= top || rect.top >= bottom) continue;

    // Anchor within an answer because older pages can extend that same row.
    const blocks = Array.from(row.querySelectorAll<HTMLElement>(markdownBlocks));
    const block =
      blocks.find(element => {
        const bounds = element.getBoundingClientRect();
        return bounds.bottom > top && bounds.top < bottom;
      }) ?? row;

    const neighbors = [row.nextElementSibling, row.previousElementSibling]
      .filter(
        (element): element is HTMLElement => element instanceof HTMLElement && element.hasAttribute('data-message-key')
      )
      .map(element => ({ row: element, offset: element.getBoundingClientRect().top - top }));

    return {
      row,
      block,
      text: block.textContent ?? '',
      blockIndex: blocks.indexOf(block),
      blockCount: blocks.length,
      firstSegment: rows.get(row.dataset.messageKey!)?.[0]?.id,
      blockOffset: block.getBoundingClientRect().top - top,
      rowOffset: rect.top - top,
      neighbors
    };
  }

  return undefined;
};

/** Keeps the reader's position when rendered messages or viewport dimensions change. */
export const useConversationScroll = ({
  scope,
  conversationKey,
  rowSegments,
  visible,
  loadEarlier
}: {
  scope: object;
  conversationKey: string;
  rowSegments: ConversationRowSegments;
  visible: boolean;
  loadEarlier: () => Promise<boolean>;
}) => {
  const viewport = useRef<HTMLDivElement>(null);
  const content = useRef<HTMLDivElement>(null);
  const state = useRef<ScrollState | undefined>(undefined);

  const latest = useRef({ rowSegments, loadEarlier });
  latest.current = { rowSegments, loadEarlier };

  const reconcile = useRef<() => void>(() => undefined);
  const schedule = useRef<() => void>(() => undefined);

  useLayoutEffect(() => {
    if (!state.current || state.current.scope !== scope || state.current.key !== conversationKey) {
      state.current = {
        scope,
        key: conversationKey,
        follow: true,
        interacting: false,
        top: 0,
        height: 0,
        width: 0,
        contentHeight: 0
      };
    }

    const current = state.current;
    const element = viewport.current;
    const stack = content.current;
    if (!visible || !element || !stack) return;

    let disposed = false;
    let frame: number | undefined;

    const writeTop = (top: number) => {
      const target = Math.max(0, Math.min(top, element.scrollHeight - element.clientHeight));
      if (Math.abs(element.scrollTop - target) <= 1) return;

      element.scrollTop = target;
      current.expectedTop = element.scrollTop;
    };

    const update = () => {
      if (disposed || element.clientHeight === 0 || element.clientWidth === 0) return;

      const geometryChanged =
        current.height !== element.clientHeight ||
        current.width !== element.clientWidth ||
        current.contentHeight !== element.scrollHeight;

      if (current.follow) {
        if (geometryChanged) writeTop(element.scrollHeight);
      } else if (current.anchor) {
        const { row, block, text, blockIndex, blockCount, firstSegment, rowOffset, blockOffset, neighbors } =
          current.anchor;
        const blocks = stack.contains(row) ? Array.from(row.querySelectorAll<HTMLElement>(markdownBlocks)) : [];

        // Repeated paragraphs need segment identity, not text matching alone.
        const prepended =
          firstSegment &&
          (latest.current.rowSegments.get(row.dataset.messageKey!)?.findIndex(segment => segment.id === firstSegment) ??
            -1) > 0;
        const index = blockIndex + (prepended ? blocks.length - blockCount : 0);
        const indexed = blocks[index]?.textContent?.startsWith(text) ? blocks[index] : undefined;
        const retainedBlock =
          prepended && indexed
            ? indexed
            : stack.contains(block) && block.textContent?.startsWith(text)
              ? block
              : (indexed ?? blocks.find(candidate => candidate.textContent === text));

        const fallback = stack.contains(row)
          ? { row, offset: rowOffset }
          : neighbors.find(candidate => stack.contains(candidate.row));
        const target = retainedBlock ?? fallback?.row;

        if (target) {
          const offset = retainedBlock ? blockOffset : fallback!.offset;

          // Exclude resize clamping so anchor correction preserves native scrolling.
          const clampedTop = Math.max(0, Math.min(current.top, element.scrollHeight - element.clientHeight));
          const readerMovement = element.scrollTop - clampedTop;
          writeTop(
            element.scrollTop +
              target.getBoundingClientRect().top -
              element.getBoundingClientRect().top -
              offset +
              readerMovement
          );
        }
      }

      current.anchor = current.follow ? undefined : captureAnchor(element, stack, latest.current.rowSegments);
      current.height = element.clientHeight;
      current.width = element.clientWidth;
      current.contentHeight = element.scrollHeight;
      current.top = element.scrollTop;

      if (element.scrollHeight <= element.clientHeight + 1 || element.scrollTop <= 16) {
        void latest.current.loadEarlier().then(loaded => {
          if (loaded && !disposed) schedule.current();
        });
      }
    };

    const queue = () => {
      if (disposed || frame !== undefined) return;
      frame = requestAnimationFrame(() => {
        frame = undefined;
        update();
      });
    };

    reconcile.current = update;
    schedule.current = queue;

    const interaction = (event: Event) => {
      if (event instanceof KeyboardEvent) {
        if (!['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) return;
        if (
          event.target instanceof Element &&
          event.target.closest('input, textarea, select, button, summary, [contenteditable]')
        )
          return;
      }
      if (event.type === 'pointerdown' && event.target !== element) return;

      current.interacting = true;
      current.expectedTop = undefined;
    };

    element.addEventListener('wheel', interaction, { passive: true });
    element.addEventListener('touchmove', interaction, { passive: true });
    element.addEventListener('keydown', interaction);
    element.addEventListener('pointerdown', interaction);

    const observer = new ResizeObserver(queue);
    observer.observe(element);
    observer.observe(stack);
    update();

    return () => {
      disposed = true;
      observer.disconnect();
      element.removeEventListener('wheel', interaction);
      element.removeEventListener('touchmove', interaction);
      element.removeEventListener('keydown', interaction);
      element.removeEventListener('pointerdown', interaction);
      if (frame !== undefined) cancelAnimationFrame(frame);
      reconcile.current = () => undefined;
      schedule.current = () => undefined;
    };
  }, [scope, conversationKey, visible]);

  useLayoutEffect(() => {
    if (visible) reconcile.current();
  });

  const onScroll = () => {
    const element = viewport.current;
    const stack = content.current;
    const current = state.current;
    if (!visible || !element || !stack || !current || element.clientHeight === 0) return;

    if (
      !current.interacting &&
      current.expectedTop !== undefined &&
      Math.abs(element.scrollTop - current.expectedTop) <= 1
    ) {
      current.expectedTop = undefined;
      return;
    }

    current.expectedTop = undefined;
    // Resize-generated scroll events must not change follow mode.
    if (
      !current.interacting &&
      (current.height !== element.clientHeight ||
        current.width !== element.clientWidth ||
        current.contentHeight !== element.scrollHeight)
    ) {
      schedule.current();
      return;
    }

    current.interacting = false;
    current.follow =
      element.scrollTop >= current.top - 1 && element.scrollHeight - element.scrollTop - element.clientHeight <= 48;
    current.top = element.scrollTop;
    current.anchor = current.follow ? undefined : captureAnchor(element, stack, latest.current.rowSegments);
    schedule.current();
  };

  return { viewport, content, onScroll };
};
