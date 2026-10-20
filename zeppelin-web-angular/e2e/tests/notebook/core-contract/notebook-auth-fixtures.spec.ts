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
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import {
  captureMetadata,
  notebookAuthFixtureDirectory,
  recordCaptureResult,
  writeWithoutAccountNames
} from '../../../core-contract/lifecycle/capture';
import {
  createLifecycleRecorder,
  type LifecycleFixture,
  type LifecycleRecorder
} from '../../../core-contract/lifecycle/fixture.mjs';
import { LoginTestUtil, type TestCredentials } from '../../../models/login-page.util';
import { NotebookTransportPage } from '../../../models/notebook-transport-page';
import {
  openTransportNote,
  reconnectTransport,
  waitForTransportReply
} from '../../../models/notebook-transport-page.util';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

const fixtures = [
  {
    title: 'captures permission writes by owner',
    file: 'acl-write.json',
    operations: ['PUT /api/notebook/{noteId}/permissions']
  },
  {
    title: 'captures permission writes by role',
    file: 'acl-write-role-owner.json',
    operations: ['PUT /api/notebook/{noteId}/permissions']
  },
  {
    title: 'captures an unauthenticated permission write',
    file: 'acl-write-unauthenticated.json',
    operations: ['PUT /api/notebook/{noteId}/permissions', 'PUT /api/login']
  },
  {
    title: 'captures an anonymous permission write',
    file: 'acl-write-anonymous.json',
    operations: ['PUT /api/notebook/{noteId}/permissions']
  },
  { title: 'captures a signed-in logout', file: 'rest-401-logout.json', operations: ['POST /api/login/logout'] },
  { title: 'captures a 401 with Location', file: 'rest-401-with-location.json', operations: ['REST 401 Location'] },
  {
    title: 'captures an HTTP-only session expiry',
    file: 'http-session-expired.json',
    operations: ['GET /api/security/ticket', 'GET /api/login', 'POST /api/login/logout', 'GET_NOTE', 'NOTE']
  },
  {
    title: 'captures a global AUTH_INFO',
    file: 'ws-global-auth-info.json',
    operations: ['COMMIT_PARAGRAPH', 'AUTH_INFO', 'GET_NOTE', 'NOTE']
  },
  {
    title: 'captures a global ERROR_INFO',
    file: 'ws-global-error-info.json',
    operations: ['RELOAD_NOTE', 'ERROR_INFO']
  },
  {
    title: 'captures read failures beside a global error',
    file: 'ws-read-correlation.json',
    operations: ['RELOAD_NOTE', 'GET_NOTE', 'NOTE', 'ERROR_INFO', 'AUTH_INFO']
  },
  {
    title: 'captures SESSION_LOGOUT after a logout',
    file: 'ws-session-logout.json',
    operations: ['POST /api/login', 'POST /api/login/logout', 'GET_NOTE', 'SESSION_LOGOUT']
  },
  {
    title: 'captures SESSION_LOGOUT after a server restart',
    file: 'session-expiry-server-restart.json',
    operations: ['POST /api/login', 'GET_NOTE', 'SESSION_LOGOUT']
  }
];

const knownExclusions = [
  'Accounts are named by role. Account names are replaced with <user>, and ticket, principal and role values are masked, so a ticket change or an unchanged authentication state is shown by server behaviour, not by values.',
  'Shell-owned AUTH_INFO, ERROR_INFO and SESSION_LOGOUT are observations of the host, not Shared Notebook Core events.'
];

interface Accounts {
  owner: TestCredentials;
  nonOwner: TestCredentials;
  roleMember: TestCredentials;
  names: string[];
}

/** Accounts from the capture server's shiro.ini, chosen by role: the template provides one of each. */
const accounts = async (): Promise<Accounts | null> => {
  const all = Object.values(await LoginTestUtil.getTestCredentials()).filter(account => account.roles?.length);
  const [owner] = all;
  const nonOwner = all.find(account => account !== owner && !account.roles!.includes('role2'));
  const roleMember = all.find(account => account !== owner && account.roles!.includes('role2'));
  return owner && nonOwner && roleMember
    ? { owner, nonOwner, roleMember, names: all.map(account => account.username) }
    : null;
};

const authMetadata = async (
  page: Page,
  scenario: string,
  operations: string[],
  extra: Record<string, unknown> = {}
) => {
  const metadata = await captureMetadata(page, scenario, operations);
  const { directories: _directories, serverPidFile: _serverPidFile, ...environment } = metadata.environment;
  return { ...metadata, knownExclusions, environment, ...extra } as LifecycleFixture['metadata'];
};

const signIn = async (context: BrowserContext, account: TestCredentials) => {
  const response = await context.request.post('/api/login', {
    form: { userName: account.username, password: account.password }
  });
  expect(response.status()).toBe(200);
};

/** A page outside the Angular app, so recorded REST is only what the scenario sends. */
const restActor = async (browser: Browser, account?: TestCredentials) => {
  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  if (account) await signIn(context, account);
  const page = await context.newPage();
  await page.goto('/api/version');
  return { context, page };
};

/** A signed-in Angular page whose socket the test can drive with the page's own ticket. */
const appActor = async (browser: Browser, account: TestCredentials) => {
  const { context, page } = await restActor(browser, account);
  const view = new NotebookTransportPage(page);
  await view.install();
  return { context, page, view };
};

const send = (page: Page, method: string, url: string, body?: unknown, form = false) =>
  page.evaluate(
    async ([method, url, body, form]) => {
      const response = await fetch(url, {
        method,
        headers:
          body === undefined ? {} : { 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json' },
        body: body === undefined ? undefined : form ? String(body) : JSON.stringify(body)
      });
      await response.text();
      return response.status;
    },
    [method, url, body, form] as const
  );

const createNote = async (page: Page, name: string, acl?: Record<string, string[]>) => {
  const created = await page.request.post('/api/notebook', {
    data: { notePath: `AuthCapture/${name}-${Date.now()}`, paragraphs: [{ text: '%md synthetic' }] }
  });
  expect(created.ok()).toBe(true);
  const noteId = (await created.json()).body as string;
  if (acl) expect((await page.request.put(`/api/notebook/${noteId}/permissions`, { data: acl })).ok()).toBe(true);
  return noteId;
};

const socketOpen = (view: NotebookTransportPage) =>
  expect.poll(() => view.page.evaluate(() => window.notebookCapture.socket.readyState), { timeout: 60000 }).toBe(1);

const write = async (recorder: LifecycleRecorder, file: string, names: string[]) => {
  await recorder.stop();
  writeWithoutAccountNames(recorder.snapshot(), names, path.join(notebookAuthFixtureDirectory(), file));
};

test.describe('Notebook authentication and permission fixtures', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  const contexts: BrowserContext[] = [];
  const noteIds: string[] = [];
  let authenticated = false;
  let roles: Accounts | null = null;

  test.beforeEach(async () => {
    authenticated = await LoginTestUtil.isShiroEnabled();
    roles = await accounts();
  });

  test.afterEach(async ({ browser }, info) => {
    await Promise.all(contexts.splice(0).map(context => context.close()));
    if (noteIds.length) {
      const cleanup = await browser.newContext({ storageState: { cookies: [], origins: [] } });
      if (roles) await signIn(cleanup, roles.owner);
      for (const id of noteIds.splice(0)) expect((await cleanup.request.delete(`/api/notebook/${id}`)).ok()).toBe(true);
      await cleanup.close();
    }
    const entry = fixtures.find(value => info.title.startsWith(value.title));
    if (entry && info.tags.includes('@live')) {
      recordCaptureResult(info, { file: entry.file, operations: entry.operations }, notebookAuthFixtureDirectory());
    }
  });

  const requireAccounts = () => {
    test.skip(!authenticated, 'Authenticated captures need capture/server.sh --mode auth');
    test.skip(!roles, 'The capture needs an owner, a non-owner and a role2 member account in shiro.ini');
    return roles!;
  };

  test('captures permission writes by owner and non-owner', { tag: '@live' }, async ({ page, browser }) => {
    const { owner, nonOwner, names } = requireAccounts();
    const noteId = await createNote(page, 'acl-write');
    noteIds.push(noteId);
    const ownerActor = await restActor(browser, owner);
    const other = await restActor(browser, nonOwner);
    contexts.push(ownerActor.context, other.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        ownerActor.page,
        'Permission writes by the owner account and a non-owner account',
        fixtures[0].operations
      ),
      { authentication: true }
    );
    recorder.install(ownerActor.page, 'owner');
    recorder.install(other.page, 'non-owner');
    const acl = { owners: [owner.username], readers: [], writers: [], runners: [] };
    expect(await send(ownerActor.page, 'PUT', `/api/notebook/${noteId}/permissions`, acl)).toBe(200);
    expect(await send(other.page, 'PUT', `/api/notebook/${noteId}/permissions`, acl)).toBe(403);
    await write(recorder, 'acl-write.json', names);
  });

  test('captures permission writes by role owner and non-owner', { tag: '@live' }, async ({ page, browser }) => {
    const { nonOwner, roleMember, names } = requireAccounts();
    const acl = { owners: ['role2'], readers: ['role2'], writers: ['role2'], runners: ['role2'] };
    const noteId = await createNote(page, 'acl-write-role', acl);
    noteIds.push(noteId);
    const member = await restActor(browser, roleMember);
    const other = await restActor(browser, nonOwner);
    contexts.push(member.context, other.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        member.page,
        'Permission writes when the owners list names only role2: the role2 member account is not listed by principal',
        fixtures[1].operations
      ),
      { authentication: true }
    );
    recorder.install(member.page, 'role2-member');
    recorder.install(other.page, 'non-owner');
    expect(await send(member.page, 'PUT', `/api/notebook/${noteId}/permissions`, acl)).toBe(200);
    expect(await send(other.page, 'PUT', `/api/notebook/${noteId}/permissions`, acl)).toBe(403);
    await write(recorder, 'acl-write-role-owner.json', names);
  });

  test('captures an unauthenticated permission write', { tag: '@live' }, async ({ page, browser }) => {
    const { names } = requireAccounts();
    const noteId = await createNote(page, 'acl-write-unauthenticated');
    noteIds.push(noteId);
    const visitor = await restActor(browser);
    contexts.push(visitor.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        page,
        'A permission write without a session on an authenticated server',
        fixtures[2].operations
      ),
      { authentication: true }
    );
    recorder.install(visitor.page, 'visitor');
    expect(await send(visitor.page, 'PUT', `/api/notebook/${noteId}/permissions`, { owners: [] })).toBe(405);
    await write(recorder, 'acl-write-unauthenticated.json', names);
  });

  test('captures an anonymous permission write', { tag: '@live' }, async ({ page }) => {
    test.skip(await LoginTestUtil.isShiroEnabled(), 'Anonymous captures need capture/server.sh without --mode auth');
    const noteId = await createNote(page, 'acl-write-anonymous');
    noteIds.push(noteId);
    await page.goto('/api/version');
    const recorder = createLifecycleRecorder(
      await authMetadata(page, 'A permission write in anonymous mode', fixtures[3].operations),
      { authentication: true }
    );
    recorder.install(page, 'anonymous');
    expect(
      await send(page, 'PUT', `/api/notebook/${noteId}/permissions`, {
        owners: [],
        readers: [],
        writers: [],
        runners: []
      })
    ).toBe(200);
    await write(recorder, 'acl-write-anonymous.json', []);
  });

  test('captures a signed-in logout', { tag: '@live' }, async ({ browser }) => {
    const { nonOwner, names } = requireAccounts();
    const actor = await restActor(browser, nonOwner);
    contexts.push(actor.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(actor.page, 'Logout of a signed-in non-owner account', fixtures[4].operations, {
        authorizationHeaderClear: true
      }),
      { authentication: true }
    );
    recorder.install(actor.page, 'non-owner');
    expect(await send(actor.page, 'POST', '/api/login/logout')).toBe(401);
    await write(recorder, 'rest-401-logout.json', names);
  });

  test('captures a 401 with Location', { tag: '@live' }, async () => {
    test.skip(
      true,
      'This server never answers REST with 401 and a Location header; that needs an SSO realm such as Knox'
    );
  });

  test('captures an HTTP-only session expiry', { tag: '@live' }, async ({ page, browser }) => {
    const { owner, roleMember, names } = requireAccounts();
    const noteId = await createNote(page, 'http-session-expired', {
      owners: [owner.username],
      readers: [owner.username, roleMember.username],
      writers: [owner.username],
      runners: [owner.username]
    });
    noteIds.push(noteId);
    const actor = await appActor(browser, roleMember);
    contexts.push(actor.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        actor.page,
        'HTTP-only session expiry, then a command on the existing socket and a reload',
        fixtures[6].operations,
        {
          sessionExpiry: {
            method: 'The session cookie is removed in the browser; the server session and ticket remain.'
          }
        }
      ),
      { authentication: true }
    );
    recorder.install(actor.page, 'role2-member');
    await openTransportNote(actor.view, noteId);
    await actor.context.clearCookies({ name: 'JSESSIONID' });
    await waitForTransportReply(actor.view, 'NOTE', await actor.view.send('GET_NOTE', { id: noteId }));
    await actor.page.reload();
    await expect(actor.page.locator('zeppelin-login')).toBeVisible({ timeout: 30000 });
    await waitForZeppelinReady(actor.page, { allowLoginPage: true });
    await write(recorder, 'http-session-expired.json', names);
  });

  test('captures a global AUTH_INFO', { tag: '@live' }, async ({ page, browser }) => {
    const { owner, nonOwner, names } = requireAccounts();
    const noteId = await createNote(page, 'ws-global-auth-info', {
      owners: [owner.username],
      readers: [owner.username, nonOwner.username],
      writers: [owner.username],
      runners: [owner.username]
    });
    noteIds.push(noteId);
    const paragraphId = (await (await page.request.get(`/api/notebook/${noteId}`)).json()).body.paragraphs[0]
      .id as string;
    const actor = await appActor(browser, nonOwner);
    contexts.push(actor.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        actor.page,
        'A reader-only account sends a write; the same socket then reads again',
        fixtures[7].operations
      ),
      { authentication: true }
    );
    recorder.install(actor.page, 'reader');
    await openTransportNote(actor.view, noteId);
    const authInfo = await waitForTransportReply(
      actor.view,
      'AUTH_INFO',
      await actor.view.send('COMMIT_PARAGRAPH', {
        noteId,
        id: paragraphId,
        title: '',
        paragraph: '%md changed',
        config: {},
        params: {}
      })
    );
    expect(authInfo.msgId ?? null).toBeNull();
    await expect(actor.page.getByText('Insufficient privileges', { exact: true })).toBeVisible();
    await waitForTransportReply(actor.view, 'NOTE', await actor.view.send('GET_NOTE', { id: noteId }));
    await write(recorder, 'ws-global-auth-info.json', names);
  });

  test('captures a global ERROR_INFO', { tag: '@live' }, async ({ browser }) => {
    const { owner, names } = requireAccounts();
    const actor = await appActor(browser, owner);
    contexts.push(actor.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        actor.page,
        'A reload of a missing note answered by an unattributed ERROR_INFO',
        fixtures[8].operations
      ),
      { authentication: true }
    );
    recorder.install(actor.page, 'owner');
    await actor.page.goto('/#/');
    await waitForZeppelinReady(actor.page);
    const errorInfo = await waitForTransportReply(
      actor.view,
      'ERROR_INFO',
      await actor.view.send('RELOAD_NOTE', { id: 'AUTH_CAPTURE_MISSING' })
    );
    expect(errorInfo.msgId ?? null).toBeNull();
    await expect(actor.page.locator('.ant-notification').getByText('ERROR')).toBeVisible();
    await write(recorder, 'ws-global-error-info.json', names);
  });

  test('captures read failures beside a global error', { tag: '@live' }, async ({ page, browser }) => {
    const { owner, nonOwner, names } = requireAccounts();
    const readable = await createNote(page, 'ws-read-correlation', {
      owners: [owner.username],
      readers: [owner.username, nonOwner.username],
      writers: [owner.username],
      runners: [owner.username]
    });
    const deniedNote = await createNote(page, 'ws-read-denied', {
      owners: [owner.username],
      readers: [owner.username],
      writers: [owner.username],
      runners: [owner.username]
    });
    noteIds.push(readable, deniedNote);
    const actor = await appActor(browser, nonOwner);
    contexts.push(actor.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        actor.page,
        'A read that succeeds after an unrelated global error, a missing read and a denied read',
        fixtures[9].operations
      ),
      { authentication: true }
    );
    recorder.install(actor.page, 'reader');
    await actor.page.goto('/#/');
    await waitForZeppelinReady(actor.page);
    const view = actor.view;
    const after = await view.send('RELOAD_NOTE', { id: 'AUTH_CAPTURE_MISSING' });
    await view.send('GET_NOTE', { id: readable });
    const note = await waitForTransportReply(view, 'NOTE', after);
    const global = (await view.receivedFrames(after)).find(frame => frame.op === 'ERROR_INFO');
    expect(global).toBeTruthy();
    expect(global!.msgId ?? null).toBeNull();
    const missing = await waitForTransportReply(
      view,
      'ERROR_INFO',
      await view.send('GET_NOTE', { id: 'AUTH_CAPTURE_MISSING' })
    );
    const denied = await waitForTransportReply(view, 'AUTH_INFO', await view.send('GET_NOTE', { id: deniedNote }));
    const reads = (await view.page.evaluate(() => window.notebookCapture.sent))
      .filter(frame => frame.op === 'GET_NOTE' && frame.msgId?.startsWith('capture:'))
      .map(frame => frame.msgId);
    expect([note.msgId, missing.msgId, denied.msgId]).toEqual(reads.slice(-3));
    await write(recorder, 'ws-read-correlation.json', names);
  });

  test('captures SESSION_LOGOUT after a logout', { tag: '@live' }, async ({ page, browser }) => {
    const { owner, roleMember, names } = requireAccounts();
    const noteId = await createNote(page, 'ws-session-logout', {
      owners: [owner.username],
      readers: [owner.username, roleMember.username],
      writers: [owner.username],
      runners: [owner.username]
    });
    noteIds.push(noteId);
    const viewer = await appActor(browser, roleMember);
    const second = await restActor(browser);
    contexts.push(viewer.context, second.context);
    const recorder = createLifecycleRecorder(
      await authMetadata(
        viewer.page,
        'The same account logs out and signs in again elsewhere; the first page keeps its socket and old ticket',
        fixtures[10].operations
      ),
      { authentication: true }
    );
    recorder.install(viewer.page, 'viewer');
    recorder.install(second.page, 'second-browser');
    await openTransportNote(viewer.view, noteId);
    const form = `userName=${encodeURIComponent(roleMember.username)}&password=${encodeURIComponent(roleMember.password)}`;
    expect(await send(second.page, 'POST', '/api/login', form, true)).toBe(200);
    expect(await send(second.page, 'POST', '/api/login/logout')).toBe(401);
    const dropped = await viewer.view.send('GET_NOTE', { id: noteId });
    await viewer.page.waitForTimeout(3000);
    expect((await viewer.view.receivedFrames(dropped)).filter(frame => frame.op !== 'PING')).toEqual([]);
    expect(await send(second.page, 'POST', '/api/login', form, true)).toBe(200);
    const logout = await waitForTransportReply(
      viewer.view,
      'SESSION_LOGOUT',
      await viewer.view.send('GET_NOTE', { id: noteId })
    );
    expect(logout.msgId ?? null).toBeNull();
    await write(recorder, 'ws-session-logout.json', names);
  });

  test('captures SESSION_LOGOUT after a server restart', { tag: '@live' }, async ({ page, browser }) => {
    const { owner, roleMember, names } = requireAccounts();
    const helper = process.env.ZEPPELIN_CAPTURE_HELPER;
    test.skip(!helper, 'Restart captures need ZEPPELIN_CAPTURE_HELPER, the baseline capture/server.sh');
    test.setTimeout(300000);
    const launch = JSON.parse(readFileSync(process.env.ZEPPELIN_CAPTURE_ENVIRONMENT!, 'utf8')) as {
      root: string;
      authentication: string;
      storage: string;
      port: number;
    };
    const noteId = await createNote(page, 'session-expiry-server-restart', {
      owners: [owner.username],
      readers: [owner.username, roleMember.username],
      writers: [owner.username],
      runners: [owner.username]
    });
    noteIds.push(noteId);
    const viewer = await appActor(browser, roleMember);
    contexts.push(viewer.context);
    await openTransportNote(viewer.view, noteId);
    const metadata = await authMetadata(
      viewer.page,
      'The server restarts while a page keeps its socket; the page reconnects with its old ticket',
      fixtures[11].operations,
      { restart: { method: 'capture/server.sh stop and start on the same capture root within the test' } }
    );
    const generation = await viewer.view.generation();
    for (const command of ['stop', 'start']) {
      execFileSync(
        'bash',
        [
          helper!,
          command,
          '--root',
          launch.root,
          ...(command === 'start'
            ? ['--port', String(launch.port), '--mode', launch.authentication, '--storage', launch.storage]
            : [])
        ],
        { stdio: 'pipe' }
      );
    }
    await expect.poll(() => viewer.view.generation(), { timeout: 60000 }).toBeGreaterThan(generation);
    await socketOpen(viewer.view);
    const second = await restActor(browser);
    contexts.push(second.context);
    const recorder = createLifecycleRecorder(metadata, { authentication: true });
    recorder.install(viewer.page, 'viewer');
    recorder.install(second.page, 'second-browser');
    await reconnectTransport(viewer.view);
    await socketOpen(viewer.view);
    const dropped = await viewer.view.send('GET_NOTE', { id: noteId });
    await viewer.page.waitForTimeout(3000);
    expect((await viewer.view.receivedFrames(dropped)).filter(frame => frame.op !== 'PING')).toEqual([]);
    const form = `userName=${encodeURIComponent(roleMember.username)}&password=${encodeURIComponent(roleMember.password)}`;
    expect(await send(second.page, 'POST', '/api/login', form, true)).toBe(200);
    const logout = await waitForTransportReply(
      viewer.view,
      'SESSION_LOGOUT',
      await viewer.view.send('GET_NOTE', { id: noteId })
    );
    expect(logout.msgId ?? null).toBeNull();
    await write(recorder, 'session-expiry-server-restart.json', names);
  });
});
