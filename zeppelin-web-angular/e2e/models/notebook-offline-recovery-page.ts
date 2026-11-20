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

interface ConnectivityProbe {
  documentId: string;
  attempts: number[];
  sockets: WebSocket[];
  maxOpen: number;
  maxActive: number;
  offlineAt: number | null;
  onlineAt: number | null;
}

export interface ConnectivitySnapshot extends Omit<ConnectivityProbe, 'sockets'> {
  online: boolean;
  open: number;
  active: number;
  socketStates: number[];
}

declare global {
  interface Window {
    notebookConnectivityProbe: ConnectivityProbe;
  }
}

export class NotebookOfflineRecoveryPage {
  readonly editorText: Locator;
  readonly reconnecting: Locator;
  private readonly paragraph: NotebookParagraphPage;

  constructor(readonly page: Page) {
    this.paragraph = new NotebookParagraphPage(page);
    this.editorText = this.paragraph.editorViewLines;
    this.reconnecting = page.getByText('Connecting WebSocket ...', { exact: true });
  }

  async install(): Promise<void> {
    await this.page.addInitScript(() => {
      const NativeWebSocket = window.WebSocket;
      const probe: ConnectivityProbe = {
        documentId: crypto.randomUUID(),
        attempts: [],
        sockets: [],
        maxOpen: 0,
        maxActive: 0,
        offlineAt: null,
        onlineAt: null
      };
      window.notebookConnectivityProbe = probe;
      const recordConcurrency = () => {
        probe.maxOpen = Math.max(probe.maxOpen, probe.sockets.filter(socket => socket.readyState === 1).length);
        probe.maxActive = Math.max(probe.maxActive, probe.sockets.filter(socket => socket.readyState !== 3).length);
      };
      window.addEventListener('offline', () => (probe.offlineAt = performance.now()));
      window.addEventListener('online', () => (probe.onlineAt = performance.now()));
      window.WebSocket = class extends NativeWebSocket {
        constructor(url: string | URL, protocols?: string | string[]) {
          const isNotebookSocket = new URL(String(url), window.location.href).pathname.endsWith('/ws');
          if (isNotebookSocket) probe.attempts.push(performance.now());
          super(url, protocols);
          if (!isNotebookSocket) return;
          probe.sockets.push(this);
          recordConcurrency();
          this.addEventListener('open', recordConcurrency);
          this.addEventListener('close', recordConcurrency);
        }
      };
    });
  }

  async setOffline(offline: boolean): Promise<void> {
    await this.page.context().setOffline(offline);
    if (this.page.context().browser()?.browserType().name() === 'firefox') {
      // Firefox emulates network state without emitting window connectivity events.
      // Supply those inputs explicitly; Chromium and WebKit retain native event delivery.
      await this.page.evaluate(offline => {
        window.dispatchEvent(new Event(offline ? 'offline' : 'online'));
      }, offline);
    }
  }

  async snapshot(): Promise<ConnectivitySnapshot> {
    return this.page.evaluate(() => {
      const { sockets, ...probe } = window.notebookConnectivityProbe;
      return {
        ...probe,
        online: navigator.onLine,
        open: sockets.filter(socket => socket.readyState === WebSocket.OPEN).length,
        active: sockets.filter(socket => socket.readyState !== WebSocket.CLOSED).length,
        socketStates: sockets.map(socket => socket.readyState)
      };
    });
  }

  async observe(transition: 'offlineAt' | 'onlineAt', duration: number): Promise<ConnectivitySnapshot> {
    const remaining = await this.page.evaluate(
      ({ transition, duration }) => {
        const started = window.notebookConnectivityProbe[transition];
        if (started === null) throw new Error(`No ${transition} event was observed`);
        return Math.max(0, started + duration - performance.now());
      },
      { transition, duration }
    );
    // JUSTIFIED: This real-time window measures retry attempts, not UI readiness.
    // eslint-disable-next-line playwright/no-wait-for-timeout -- A bounded observation interval must elapse to detect retry storms.
    await this.page.waitForTimeout(remaining);
    return this.snapshot();
  }

  async replaceText(text: string): Promise<void> {
    await this.paragraph.editorViewLines.click();
    const isMacintosh = await this.page.evaluate(() => navigator.userAgent.includes('Macintosh'));
    await this.page.keyboard.press(isMacintosh ? 'Meta+A' : 'Control+A');
    await this.page.keyboard.insertText(text);
  }
}
