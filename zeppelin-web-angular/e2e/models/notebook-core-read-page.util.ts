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

import { expect, APIRequestContext, Page, WebSocketRoute } from '@playwright/test';
import { E2E_TEST_FOLDER } from './base-page';

const ZEPPELIN_WS_URL_PATTERN = /\/ws(\?|$)/;

export interface CoreReadEnvelope {
  op: string;
  msgId?: string;
  data?: Record<string, unknown>;
}

/** Ops the read view may send. PING is the Shell's keep-alive. */
const CORE_READ_ALLOWED_OPS = ['GET_NOTE', 'NOTE_REVISION', 'PING'];

const parse = (message: string | Buffer): CoreReadEnvelope | null => {
  try {
    return JSON.parse(message.toString()) as CoreReadEnvelope;
  } catch {
    return null;
  }
};

/**
 * Sits between the page and the server socket. It records what the page sends and lets a test hold
 * server frames, inject frames the server never sent, and drop the connection.
 */
export class CoreReadSocketProbe {
  readonly sent: CoreReadEnvelope[] = [];
  connections = 0;
  private client: WebSocketRoute | null = null;
  private server: WebSocketRoute | null = null;
  private readonly holds: Array<{ matches: (frame: CoreReadEnvelope) => boolean; frames: string[] }> = [];

  static async install(page: Page): Promise<CoreReadSocketProbe> {
    const probe = new CoreReadSocketProbe();
    await page.routeWebSocket(ZEPPELIN_WS_URL_PATTERN, client => {
      const server = client.connectToServer();
      probe.client = client;
      probe.server = server;
      probe.connections++;
      client.onMessage(message => {
        const envelope = parse(message);
        if (envelope) probe.sent.push(envelope);
        server.send(message);
      });
      server.onMessage(message => {
        const envelope = parse(message);
        const hold = envelope && probe.holds.find(({ matches }) => matches(envelope));
        if (hold) {
          hold.frames.push(message.toString());
        } else {
          client.send(message);
        }
      });
    });
    return probe;
  }

  /** Keeps matching server frames from the page until the returned function releases them. */
  hold(matches: (frame: CoreReadEnvelope) => boolean): () => void {
    const hold = { matches, frames: [] as string[] };
    this.holds.push(hold);
    return () => {
      this.holds.splice(this.holds.indexOf(hold), 1);
      hold.frames.splice(0).forEach(frame => this.client?.send(frame));
    };
  }

  inject(frame: CoreReadEnvelope): void {
    this.client!.send(JSON.stringify(frame));
  }

  /** Closes the page's socket as a server-side drop would. The Shell reconnects on its own. */
  async dropConnection(): Promise<void> {
    await this.server!.close();
    await this.client!.close({ code: 1012, reason: 'Core read connection drop' });
  }

  sentOps(from = 0, to = this.sent.length): string[] {
    return this.sent.slice(from, to).map(({ op }) => op);
  }

  /** Ops sent in `[from, to)` that the read view must never send. */
  forbiddenOps(from = 0, to = this.sent.length): string[] {
    return this.sentOps(from, to).filter(op => !CORE_READ_ALLOWED_OPS.includes(op));
  }

  lastRead(op: 'GET_NOTE' | 'NOTE_REVISION'): CoreReadEnvelope {
    const read = [...this.sent].reverse().find(envelope => envelope.op === op);
    if (!read) throw new Error(`No ${op} was sent`);
    return read;
  }
}

/**
 * Resolves once the page has handled the next `count` frames of `op`. The SDK logs every received op,
 * which is the only signal that a frame expected to change nothing has actually been processed.
 */
export const waitForReceived = (page: Page, op: string, count = 1): Promise<void> => {
  let seen = 0;
  return page
    .waitForEvent('console', { predicate: message => message.text() === `Receive: ${op}` && ++seen === count })
    .then(() => undefined);
};

export const isNoteFrame = (noteId: string) => (frame: CoreReadEnvelope) =>
  frame.op === 'NOTE' && (frame.data?.note as { id?: string } | undefined)?.id === noteId;

const savedParagraph = (title: string, results: Array<{ type: string; data: string }>, graph?: unknown) => ({
  title,
  text: `%md ${title}`,
  status: 'FINISHED',
  config: graph ? { results: { '0': { graph } } } : {},
  results: { code: 'SUCCESS', msg: results }
});

/** Imports a note whose paragraphs already hold saved results, so no interpreter has to run. */
export const importSavedResultsNote = async (request: APIRequestContext, name: string): Promise<string> => {
  const note = {
    name,
    paragraphs: [
      savedParagraph('Markdown', [{ type: 'HTML', data: '<p><strong>Saved heading</strong></p>' }]),
      savedParagraph('Table', [{ type: 'TABLE', data: 'x\ty\n1\t3\n2\t5\n' }], {
        mode: 'lineChart',
        height: 250,
        optionOpen: false
      }),
      savedParagraph('Text', [{ type: 'TEXT', data: 'saved text output' }]),
      savedParagraph('Angular', [{ type: 'ANGULAR', data: '<div>angular output</div>' }])
    ],
    config: {}
  };
  const response = await request.post('/api/notebook/import', {
    params: { notePath: `/${E2E_TEST_FOLDER}/${name}` },
    data: note
  });
  expect(response.ok(), `Notebook import failed (${response.status()}): ${await response.text()}`).toBe(true);
  return (await response.json()).body as string;
};

export const firstParagraphId = async (request: APIRequestContext, noteId: string): Promise<string> => {
  const response = await request.get(`/api/notebook/${noteId}`);
  expect(response.ok()).toBe(true);
  return (await response.json()).body.paragraphs[0].id as string;
};

export const renameParagraph = async (
  request: APIRequestContext,
  noteId: string,
  paragraphId: string,
  title: string
): Promise<void> => {
  const response = await request.put(`/api/notebook/${noteId}/paragraph/${paragraphId}`, { data: { title } });
  expect(response.ok(), `Paragraph update failed (${response.status()})`).toBe(true);
};

/** Returns the revision id, or null when the notebook storage keeps no revisions. */
export const checkpoint = async (
  request: APIRequestContext,
  noteId: string,
  message: string
): Promise<string | null> => {
  const capabilities = await request.get('/api/notebook/capabilities');
  if (!(await capabilities.json()).body.isRevisionSupported) return null;
  const response = await request.post(`/api/notebook/${noteId}/revision`, { data: { commitMessage: message } });
  expect(response.ok(), `Checkpoint failed (${response.status()})`).toBe(true);
  return (await response.json()).body as string;
};

export const setNotePermissions = async (
  request: APIRequestContext,
  noteId: string,
  principal: string
): Promise<void> => {
  const lists = { owners: [principal], readers: [principal], runners: [principal], writers: [principal] };
  const response = await request.put(`/api/notebook/${noteId}/permissions`, { data: lists });
  expect(response.ok(), `Permission update failed (${response.status()})`).toBe(true);
};

export const deleteNote = async (request: APIRequestContext, noteId: string | undefined): Promise<void> => {
  if (noteId) await request.delete(`/api/notebook/${noteId}`);
};
