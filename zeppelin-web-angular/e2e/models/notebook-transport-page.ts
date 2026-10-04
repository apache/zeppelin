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
import type { Locator, Page } from '@playwright/test';
import { NotebookParagraphPage } from './notebook-paragraph-page';

export interface TransportEnvelope {
  op: string;
  msgId?: string;
  data: Record<string, unknown>;
}
declare global {
  interface Window {
    notebookCapture: {
      socket: WebSocket;
      frames: TransportEnvelope[];
      sent: TransportEnvelope[];
      generation: number;
      command: number;
    };
  }
}

export class NotebookTransportPage {
  readonly paragraphs: Locator;
  readonly editorText: Locator;

  constructor(readonly page: Page) {
    const paragraphPage = new NotebookParagraphPage(page);
    this.editorText = paragraphPage.editorViewLinesAll;
    this.paragraphs = paragraphPage.paragraphContainers;
  }

  async install(): Promise<void> {
    await this.page.addInitScript(() => {
      const NativeWebSocket = window.WebSocket;
      window.WebSocket = class extends NativeWebSocket {
        constructor(url: string | URL, protocols?: string | string[]) {
          super(url, protocols);
          if (new URL(String(url)).pathname !== '/ws') return;
          const previous = window.notebookCapture;
          window.notebookCapture = {
            socket: this,
            frames: previous?.frames ?? [],
            sent: previous?.sent ?? [],
            generation: (previous?.generation ?? 0) + 1,
            command: previous?.command ?? 0
          };
          this.addEventListener('message', event => {
            window.notebookCapture.frames.push(JSON.parse(String(event.data)) as TransportEnvelope);
          });
        }
        override send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
          if (typeof data === 'string') window.notebookCapture.sent.push(JSON.parse(data) as TransportEnvelope);
          super.send(data);
        }
      };
    });
  }

  async navigateToNote(noteId: string, revisionId?: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}${revisionId ? `/revision/${revisionId}` : ''}`);
  }

  async receivedFrames(after = 0): Promise<TransportEnvelope[]> {
    return this.page.evaluate(start => window.notebookCapture.frames.slice(start), after);
  }

  async count(): Promise<number> {
    return this.page.evaluate(() => window.notebookCapture?.frames.length ?? 0);
  }

  async send(op: string, data: Record<string, unknown>): Promise<number> {
    const after = await this.count();
    await this.page.evaluate(
      ({ operation, payload }) => {
        const capture = window.notebookCapture;
        const envelope = capture.sent.at(-1);
        if (!envelope || capture.socket.readyState !== WebSocket.OPEN) throw new Error('Notebook socket is not ready');
        capture.socket.send(
          JSON.stringify({ ...envelope, op: operation, data: payload, msgId: `capture:${++capture.command}` })
        );
      },
      { operation: op, payload: data }
    );
    return after;
  }

  async canonical(noteId: string): Promise<{ id: string; paragraphs: { id: string; text: string }[] }> {
    return this.page.evaluate(async id => {
      const response = await fetch(`/api/notebook/${id}`);
      if (!response.ok) throw new Error(`Canonical note fetch failed: ${response.status}`);
      return (await response.json()).body;
    }, noteId);
  }

  async disconnect(): Promise<number> {
    return this.page.evaluate(() => {
      const capture = window.notebookCapture;
      capture.socket.close(4001, 'Lifecycle capture disconnect');
      return capture.generation;
    });
  }

  async generation(): Promise<number> {
    return this.page.evaluate(() => window.notebookCapture.generation);
  }
}
