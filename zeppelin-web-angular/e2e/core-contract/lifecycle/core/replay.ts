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
import { expect, type Browser, type Page } from '@playwright/test';
import type { LifecycleFixture, LifecycleFault } from '../fixture.mjs';
import { replayLifecycleTrace, type LifecycleReplayConsumer } from '../replay-in-browser';

type ParagraphState = {
  id: string;
  text: string;
  status: string;
  isDirty: boolean;
  progress: number;
  language?: string;
  results?: { type: string; data: string }[];
  resultConfigs?: unknown;
};
type CoreState = {
  noteId: string;
  revisionId: string | null;
  phase: string;
  title: string | null;
  error: string | null;
  paragraphs: ParagraphState[];
  noteForms: unknown;
  noteParams: unknown;
  lookAndFeel: string;
  personalizedMode: boolean;
  scheduler?: { cron?: string; releaseResource: boolean };
  collaborativeUsers?: string[];
};
type CoreObservation = {
  snapshot: CoreState;
  events: { op: string; snapshot: CoreState }[];
  recoveryRequested: boolean;
};
type CanonicalNote = {
  id: string;
  name: string;
  paragraphs: {
    id: string;
    text: string;
    status: string;
    progress: number;
    results?: { msg?: { type: string; data: string }[] };
    config: { results?: unknown; editorSetting?: { language?: string } };
  }[];
  noteForms: unknown;
  noteParams: unknown;
  config: {
    looknfeel: string;
    personalizedMode?: string;
    isZeppelinNotebookCronEnable?: boolean;
    cron?: string;
    releaseresource?: boolean;
  };
};

declare global {
  interface Window {
    lifecycleCore: {
      enterContext(context: { state: string; noteId: string; revisionId: string | null }): void;
      beforeSend(envelope: unknown): void;
      observe(): CoreObservation;
    };
  }
}

const observe = (page: Page) => page.evaluate(() => window.lifecycleCore.observe());
const canonicalLanguage = (paragraph: CanonicalNote['paragraphs'][number]) => {
  if (paragraph.config.editorSetting?.language) return paragraph.config.editorSetting.language;
  const directive = paragraph.text.trimStart().match(/^%(\w+)/)?.[1];
  return directive === 'md' ? 'markdown' : directive;
};
const canonicalState = (note: CanonicalNote) => ({
  noteId: note.id,
  title: note.name,
  paragraphs: note.paragraphs.map(paragraph => ({
    id: paragraph.id,
    text: paragraph.text,
    status: paragraph.status,
    progress: paragraph.progress,
    language: canonicalLanguage(paragraph),
    results: paragraph.results?.msg ?? [],
    resultConfigs: paragraph.config.results,
    isDirty: false
  })),
  noteForms: note.noteForms,
  noteParams: note.noteParams,
  lookAndFeel: note.config.looknfeel,
  personalizedMode: note.config.personalizedMode === 'true',
  scheduler: note.config.isZeppelinNotebookCronEnable
    ? { cron: note.config.cron, releaseResource: Boolean(note.config.releaseresource) }
    : undefined
});
const projectedState = (snapshot: CoreState) => ({
  noteId: snapshot.noteId,
  title: snapshot.title,
  paragraphs: snapshot.paragraphs.map(paragraph => ({
    id: paragraph.id,
    text: paragraph.text,
    status: paragraph.status,
    progress: paragraph.progress,
    language: paragraph.language,
    results: paragraph.results ?? [],
    resultConfigs: paragraph.resultConfigs,
    isDirty: paragraph.isDirty
  })),
  noteForms: snapshot.noteForms,
  noteParams: snapshot.noteParams,
  lookAndFeel: snapshot.lookAndFeel,
  personalizedMode: snapshot.personalizedMode,
  scheduler: snapshot.scheduler
});

export const replayCollaborationCore = async (browser: Browser, fixture: LifecycleFixture, script: string) => {
  const selected = fixture.records.filter(record => {
    if (record.kind !== 'websocket' || record.websocket.direction !== 'receive') return false;
    return ['PATCH_PARAGRAPH', 'NOTE_UPDATED'].includes(JSON.parse(record.websocket.payloadText).op);
  });
  expect(selected).toHaveLength(3);
  const recovery = fixture.records.find(record => {
    if (record.sequence <= selected[selected.length - 1].sequence) return false;
    if (record.kind !== 'websocket' || record.websocket.direction !== 'send') return false;
    return JSON.parse(record.websocket.payloadText).op === 'GET_NOTE';
  })!;
  expect(recovery).toBeDefined();
  const faults: LifecycleFault[] = selected.map((record, index) => ({
    sequence: record.sequence,
    copies: index === 1 ? 0 : 2,
    delayMs: index === 0 ? 5 : 0,
    afterSequence: recovery.sequence - 1
  }));
  const copies = new Map(faults.map(fault => [fault.sequence, fault.copies!]));
  const pages = new Map<string, Page>();
  const beforeRecovery = new Map<string, CoreObservation>();
  const afterRecovery = new Map<string, CoreObservation>();
  const browserErrors: Error[] = [];

  const consumer: LifecycleReplayConsumer = {
    initialize: async (page, sessionId) => {
      pages.set(sessionId, page);
      page.on('pageerror', error => browserErrors.push(error));
      await page.addScriptTag({ content: script });
    },
    enterContext: async (page, context) => {
      await page.evaluate(value => window.lifecycleCore.enterContext(value), context);
    },
    beforeSend: async (page, record) => {
      if (record.sequence === recovery.sequence) {
        // Settle every declared fault before the captured GET_NOTE recovery starts.
        for (const [sessionId, viewer] of pages) {
          const expected = fixture.records
            .filter(
              entry =>
                entry.sequence < recovery.sequence &&
                entry.sessionId === sessionId &&
                entry.kind === 'websocket' &&
                entry.websocket.direction === 'receive'
            )
            .reduce((count, entry) => count + (copies.get(entry.sequence) ?? 1), 0);
          await expect.poll(() => viewer.evaluate(() => window.traceFrames.length)).toBe(expected);
          beforeRecovery.set(sessionId, await observe(viewer));
        }
      }
      await page.evaluate(
        payload => window.lifecycleCore.beforeSend(JSON.parse(payload)),
        record.websocket.payloadText
      );
    },
    complete: async viewers => {
      for (const [sessionId, page] of viewers) afterRecovery.set(sessionId, await observe(page));
    }
  };

  const frames = await replayLifecycleTrace(browser, fixture, faults, consumer);
  expect(browserErrors).toEqual([]);
  expect(beforeRecovery.size).toBe(2);
  const sender = beforeRecovery.get('viewer-a')!;
  const follower = beforeRecovery.get('viewer-b')!;
  expect(sender.snapshot.paragraphs[0].text).toBe('%md modified');
  expect(sender.snapshot.paragraphs[0].isDirty).toBe(true);
  expect(sender.snapshot.title).not.toBe(follower.snapshot.title);
  const changes = follower.events.filter(event => ['PATCH_PARAGRAPH', 'NOTE_UPDATED'].includes(event.op));
  expect(changes.map(event => event.op)).toEqual([
    'NOTE_UPDATED',
    'NOTE_UPDATED',
    'PATCH_PARAGRAPH',
    'PATCH_PARAGRAPH'
  ]);
  expect(changes[2].snapshot.paragraphs[0].text).toBe('%md modified');

  for (const session of fixture.sessions) {
    const canonical = fixture.records.findLast(
      record =>
        record.sessionId === session.id &&
        record.kind === 'rest' &&
        record.rest.direction === 'response' &&
        /^\/api\/notebook\/[^/]+$/.test(record.rest.request.url)
    );
    expect(canonical?.kind).toBe('rest');
    if (canonical?.kind !== 'rest') throw new Error('A canonical server REST response is required');
    expect(canonical.rest.status).toBe(200);
    expect(canonical.sequence).toBeGreaterThan(recovery.sequence);
    const note = (canonical.rest.bodyJson as { status: string; body: CanonicalNote }).body;
    const result = afterRecovery.get(session.id)!;
    expect(result.recoveryRequested).toBe(false);
    expect(result.snapshot.phase).toBe('ready');
    expect(result.snapshot.revisionId).toBeNull();
    expect(result.snapshot.error).toBeNull();
    const mode = frames.get(session.id)!.findLast(envelope => envelope.op === 'COLLABORATIVE_MODE_STATUS')!;
    const modeData = mode.data as { status: boolean; users?: string[] };
    expect(result.snapshot.collaborativeUsers).toEqual(modeData.status ? modeData.users : undefined);
    expect(projectedState(result.snapshot)).toEqual(canonicalState(note));
  }
  const domainState = (snapshot: CoreState) =>
    Object.fromEntries(Object.entries(snapshot).filter(([key]) => key !== 'version'));
  expect(domainState(afterRecovery.get('viewer-a')!.snapshot)).toEqual(
    domainState(afterRecovery.get('viewer-b')!.snapshot)
  );
  return {
    faults,
    beforeRecovery: Object.fromEntries(beforeRecovery),
    afterRecovery: Object.fromEntries(afterRecovery)
  };
};
