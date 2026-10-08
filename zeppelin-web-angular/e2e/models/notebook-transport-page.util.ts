/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { expect, type Page, type WebSocketRoute } from '@playwright/test';
import { waitForZeppelinReady } from '../utils';
import { NotebookTransportPage, type TransportEnvelope } from './notebook-transport-page';

export const waitForTransportReply = async (
  view: NotebookTransportPage,
  op: string,
  after: number
): Promise<TransportEnvelope> => {
  await expect
    .poll(async () => (await view.receivedFrames(after)).some(frame => frame.op === op), {
      timeout: 15000,
      message: `Waiting for ${op}`
    })
    .toBe(true);
  return (await view.receivedFrames(after)).find(frame => frame.op === op)!;
};

export const openTransportNote = async (
  view: NotebookTransportPage,
  noteId: string,
  revisionId?: string
): Promise<void> => {
  const after = await view.count();
  await view.navigateToNote(noteId, revisionId);
  await waitForZeppelinReady(view.page);
  await waitForTransportReply(view, revisionId ? 'NOTE_REVISION' : 'NOTE', after);
  await expect(view.editorText.first()).toBeVisible({ timeout: 15000 });
};

export const reconnectTransport = async (view: NotebookTransportPage): Promise<number> => {
  const generation = await view.disconnect();
  await expect.poll(() => view.generation(), { timeout: 15000 }).toBe(generation + 1);
  return generation;
};

/** Holds one real NOTE unchanged; all other server frames retain their upstream order. */
export class NoteDeliveryProbe {
  private noteId?: string;
  private held?: { socket: WebSocketRoute; message: string | Buffer };

  hold(noteId: string): void {
    if (this.noteId || this.held) throw new Error('A NOTE hold is already armed');
    this.noteId = noteId;
  }

  receive(socket: WebSocketRoute, message: string | Buffer): void {
    const envelope = JSON.parse(message.toString()) as TransportEnvelope;
    if (
      this.noteId !== undefined &&
      envelope.op === 'NOTE' &&
      (envelope.data.note as { id?: string } | null)?.id === this.noteId
    ) {
      this.held = { socket, message };
      this.noteId = undefined;
      return;
    }
    socket.send(message);
  }

  hasHeldNote(): boolean {
    return this.held !== undefined;
  }

  release(): void {
    if (!this.held) throw new Error('No matching NOTE has been held');
    this.held.socket.send(this.held.message);
    this.held = undefined;
  }
}

export const installNoteDeliveryProbe = async (page: Page): Promise<NoteDeliveryProbe> => {
  const probe = new NoteDeliveryProbe();
  await page.routeWebSocket(/\/ws(\?|$)/, socket => {
    const server = socket.connectToServer();
    socket.onMessage(message => server.send(message));
    server.onMessage(message => probe.receive(socket, message));
  });
  return probe;
};

export const editAssociatedTransportNote = async (
  view: NotebookTransportPage,
  noteId: string,
  name: string
): Promise<void> => {
  const added = await waitForTransportReply(
    view,
    'PARAGRAPH_ADDED',
    await view.send('INSERT_PARAGRAPH', { index: 1, config: {} })
  );
  const paragraphId = (added.data.paragraph as { id: string }).id;
  await waitForTransportReply(
    view,
    'PARAGRAPH_MOVED',
    await view.send('MOVE_PARAGRAPH', { id: paragraphId, index: 0 })
  );
  await waitForTransportReply(view, 'PARAGRAPH_REMOVED', await view.send('PARAGRAPH_REMOVE', { id: paragraphId }));
  await waitForTransportReply(
    view,
    'NOTE_UPDATED',
    await view.send('NOTE_UPDATE', { id: noteId, name, config: { looknfeel: 'default' } })
  );
};
