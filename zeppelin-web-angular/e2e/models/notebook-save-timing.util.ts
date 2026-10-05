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

import { expect, Page, WebSocketRoute } from '@playwright/test';

const IDLE_SAVE_TIMEOUT_MS = 30000;
const PROXY_TIMEOUT_MS = 15000;
const COMMIT_STABILITY_MS = 2000;
const ZEPPELIN_WS_URL_PATTERN = /\/ws(\?|$)/;

interface NotebookSocketMessage {
  op?: string;
  msgId?: string;
  data?: {
    id?: string;
    noteId?: string;
    paragraph?: string | Record<string, unknown>;
    status?: boolean;
  };
}

interface CommitParagraphMessage extends NotebookSocketMessage {
  op: 'COMMIT_PARAGRAPH';
  msgId: string;
  data: {
    id: string;
    noteId: string;
    paragraph: string;
  };
}

export class CommitParagraphSocketProbe {
  private shouldHoldFirstCommitResponse = false;
  private shouldQueueServerMessages = false;
  private heldResponseMsgId: string | null = null;
  private collaborativeModeSignal: string | null = null;
  private readonly commits: CommitParagraphMessage[] = [];
  private readonly forwardedResponseMsgIds: string[] = [];
  private readonly heldResponses = new Map<string, { socket: WebSocketRoute; message: string | Buffer }>();
  private readonly queuedResponses: Array<{ socket: WebSocketRoute; message: string | Buffer }> = [];

  handleClientMessage(server: WebSocketRoute, message: string | Buffer): void {
    const parsed = parseSocketMessage(message);
    if (parsed?.op === 'PATCH_PARAGRAPH') {
      this.collaborativeModeSignal ??= 'client sent PATCH_PARAGRAPH';
    }
    if (isCommitParagraphMessage(parsed)) {
      this.commits.push(parsed);
      if (this.shouldHoldFirstCommitResponse && this.heldResponseMsgId === null) {
        this.heldResponseMsgId = parsed.msgId;
      }
    }
    server.send(message);
  }

  handleServerMessage(socket: WebSocketRoute, message: string | Buffer): void {
    const parsed = parseSocketMessage(message);
    if (parsed?.op === 'COLLABORATIVE_MODE_STATUS' && parsed.data?.status === true) {
      this.collaborativeModeSignal ??= 'server sent COLLABORATIVE_MODE_STATUS with status true';
    }
    if (parsed?.op === 'PARAGRAPH' && parsed.msgId) {
      if (parsed.msgId === this.heldResponseMsgId && !this.heldResponses.has(parsed.msgId)) {
        this.heldResponses.set(parsed.msgId, { socket, message });
        this.shouldQueueServerMessages = true;
        return;
      }
    }
    // Preserve server order while the delayed response is delivered and observed in isolation.
    if (this.shouldQueueServerMessages) {
      this.queuedResponses.push({ socket, message });
      return;
    }
    if (parsed?.op === 'PARAGRAPH' && parsed.msgId) {
      this.forwardedResponseMsgIds.push(parsed.msgId);
    }
    socket.send(message);
  }

  holdFirstCommitParagraphResponse(): void {
    this.shouldHoldFirstCommitResponse = true;
  }

  async waitForCommitCount(expectedCount: number): Promise<CommitParagraphMessage[]> {
    await expect
      .poll(() => this.collaborativeModeSignal !== null || this.commits.length >= expectedCount, {
        timeout: IDLE_SAVE_TIMEOUT_MS
      })
      .toBe(true);
    if (this.collaborativeModeSignal !== null) {
      throw new Error(
        `Notebook entered collaborative mode (${this.collaborativeModeSignal}); edits are sent as PATCH_PARAGRAPH, so no COMMIT_PARAGRAPH will arrive`
      );
    }
    return this.commits.slice();
  }

  async waitForHeldResponse(msgId: string): Promise<void> {
    await expect.poll(() => this.heldResponses.has(msgId), { timeout: PROXY_TIMEOUT_MS }).toBe(true);
  }

  async waitForForwardedResponse(msgId: string): Promise<void> {
    await expect
      .poll(() => this.forwardedResponseCount(msgId), { timeout: PROXY_TIMEOUT_MS })
      .toBeGreaterThanOrEqual(1);
  }

  async expectCommitCountToStay(expectedCount: number): Promise<void> {
    const stableUntil = Date.now() + COMMIT_STABILITY_MS;
    await expect
      .poll(() => (this.commits.length !== expectedCount || Date.now() >= stableUntil ? this.commits.length : null), {
        timeout: PROXY_TIMEOUT_MS
      })
      .toBe(expectedCount);
  }

  commitCount(): number {
    return this.commits.length;
  }

  forwardedResponseCount(msgId: string): number {
    return this.forwardedResponseMsgIds.filter(forwardedMsgId => forwardedMsgId === msgId).length;
  }

  releaseHeldResponseWithParagraphTitle(msgId: string, title: string): void {
    const held = this.heldResponses.get(msgId);
    if (!held) {
      throw new Error(`No held PARAGRAPH response for msgId ${msgId}`);
    }
    const parsed = parseSocketMessage(held.message);
    if (parsed?.op !== 'PARAGRAPH' || typeof parsed.data?.paragraph !== 'object') {
      throw new Error(`Held response for msgId ${msgId} is not a PARAGRAPH snapshot`);
    }
    // The held response only echoes the committed text, so a marker title lets the test observe when it is applied.
    const paragraph = parsed.data.paragraph;
    const config = typeof paragraph.config === 'object' && paragraph.config !== null ? paragraph.config : {};
    paragraph.title = title;
    paragraph.config = { ...config, title: true };

    this.heldResponses.delete(msgId);
    this.heldResponseMsgId = null;
    this.shouldHoldFirstCommitResponse = false;
    this.forwardedResponseMsgIds.push(msgId);
    held.socket.send(JSON.stringify(parsed));
  }

  releaseQueuedResponses(): void {
    this.shouldQueueServerMessages = false;
    for (const queued of this.queuedResponses.splice(0)) {
      this.handleServerMessage(queued.socket, queued.message);
    }
  }
}

export const installCommitParagraphProbe = async (page: Page): Promise<CommitParagraphSocketProbe> => {
  const probe = new CommitParagraphSocketProbe();
  await page.routeWebSocket(ZEPPELIN_WS_URL_PATTERN, socket => {
    const server = socket.connectToServer();
    socket.onMessage(message => probe.handleClientMessage(server, message));
    server.onMessage(message => probe.handleServerMessage(socket, message));
  });
  return probe;
};

const parseSocketMessage = (message: string | Buffer): NotebookSocketMessage | null => {
  try {
    return JSON.parse(message.toString()) as NotebookSocketMessage;
  } catch {
    return null;
  }
};

const isCommitParagraphMessage = (message: NotebookSocketMessage | null): message is CommitParagraphMessage => {
  return (
    message?.op === 'COMMIT_PARAGRAPH' &&
    typeof message.msgId === 'string' &&
    typeof message.data?.id === 'string' &&
    typeof message.data.noteId === 'string' &&
    typeof message.data.paragraph === 'string'
  );
};
