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

import { QueryList } from '@angular/core';
import { convertToParamMap } from '@angular/router';
import { DatasetType, Note, OP, ParagraphItem, ParagraphState } from '@zeppelin/sdk';
import { BehaviorSubject, EMPTY, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { NgZService } from '../../../../services/ng-z.service';

vi.mock('@zeppelin/services', () => ({
  MessageService: class {},
  HeliumService: class {},
  NgZService: class {},
  NoteStatusService: class {},
  ReactFeatureService: class {}
}));
vi.mock('@zeppelin/core', async () => ({
  ...(await import('../../../../core/paragraph-base/paragraph-base')),
  ...(await import('../../../../core/message-listener/message-listener')),
  publishedSymbol: Symbol('published')
}));
vi.mock('../../share/result/result.component', () => ({ NotebookParagraphResultComponent: class {} }));

import { PublishedParagraphComponent } from './paragraph.component';

const snapshot = (noteId: string, status: ParagraphState, text: string): Note => ({
  note: {
    id: noteId,
    name: noteId,
    paragraphs: [
      {
        id: 'same-paragraph',
        status,
        text: '',
        results: { msg: [{ type: DatasetType.TEXT, data: text }] },
        config: {},
        settings: { params: {}, forms: {} }
      } as ParagraphItem
    ]
  } as Note['note']
});

const components: PublishedParagraphComponent[] = [];
afterEach(() => components.splice(0).forEach(component => component.ngOnDestroy()));

const setup = (useReact = false, ngZService = new NgZService()) => {
  const params = new BehaviorSubject({ noteId: 'note-A', paragraphId: 'same-paragraph' });
  const component = new PublishedParagraphComponent(
    { receive: () => EMPTY, receiveEnvelope: () => EMPTY, getNote: vi.fn() } as never,
    { params, queryParamMap: of(convertToParamMap({})) } as never,
    {} as never,
    {} as never,
    {} as never,
    { isEnabled: () => useReact } as never,
    { isParagraphRunning: (p: ParagraphItem) => p.status === 'RUNNING' } as never,
    ngZService,
    { markForCheck: vi.fn() } as never
  );
  component.notebookParagraphResultComponents = new QueryList();
  components.push(component);
  return { component, params, ngZService };
};

const append = (component: PublishedParagraphComponent, noteId: string, data: string) =>
  component.onParagraphAppendOutput({ noteId, paragraphId: 'same-paragraph', index: 0, data });

describe('Published paragraph authoritative snapshots', () => {
  it('resumes from a same-note refreshed snapshot instead of cached output', () => {
    const { component } = setup();
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'RUNNING', 'old') });
    append(component, 'note-A', ' tail');
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'RUNNING', 'fresh') });
    append(component, 'note-A', ' next');
    expect(component.results).toEqual([{ type: DatasetType.TEXT, data: 'fresh next' }]);
  });

  it('unfreezes terminal output when the reused route loads a running paragraph', () => {
    const { component, params } = setup();
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'FINISHED', 'A final') });
    append(component, 'note-A', ' ignored');
    params.next({ noteId: 'note-B', paragraphId: 'same-paragraph' });
    component.getNote({ op: OP.NOTE, data: snapshot('note-B', 'RUNNING', 'B first') });
    append(component, 'note-B', ' second');
    expect(component.results).toEqual([{ type: DatasetType.TEXT, data: 'B first second' }]);
  });

  it('rejects stale NOTE and stream events while waiting for the new route snapshot', () => {
    const { component, params } = setup();
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'RUNNING', 'A first') });
    append(component, 'note-A', ' tail');
    params.next({ noteId: 'note-B', paragraphId: 'same-paragraph' });
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'RUNNING', 'stale A') });
    append(component, 'note-A', ' stale tail');
    append(component, 'note-B', ' before snapshot');
    expect(component.results).toEqual([]);
    component.getNote({ op: OP.NOTE, data: snapshot('note-B', 'RUNNING', 'B first') });
    append(component, 'note-A', ' foreign');
    append(component, 'note-B', ' second');
    expect(component.results).toEqual([{ type: DatasetType.TEXT, data: 'B first second' }]);
  });

  it('does not hydrate a removed server model when a published view is reopened in the same application', () => {
    const service = new NgZService();
    const first = setup(false, service);
    first.component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'FINISHED', 'old output') });
    first.component.angularObjectUpdate({
      noteId: 'note-A',
      paragraphId: 'same-paragraph',
      interpreterGroupId: 'spark',
      angularObject: {
        name: 'controlValue',
        object: 'deleted-while-away',
        noteId: 'note-A',
        paragraphId: 'same-paragraph'
      }
    });
    components.pop()!.ngOnDestroy();

    const second = setup(false, service);
    second.component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'FINISHED', 'saved output') });
    const context: Record<string, unknown> = {};
    service.bindParagraphContext('same-paragraph', context);

    expect(context.controlValue).toBeUndefined();
  });

  it('releases the previous published paragraph when the route changes within a notebook', () => {
    const { component, params, ngZService } = setup();
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'FINISHED', 'first output') });
    ngZService.setContextValue('privateValue', 'previous-paragraph', 'same-paragraph');
    params.next({ noteId: 'note-A', paragraphId: 'next-paragraph' });
    const next = snapshot('note-A', 'FINISHED', 'next output');
    next.note!.paragraphs[0].id = 'next-paragraph';
    component.getNote({ op: OP.NOTE, data: next });
    const staleOutput: Record<string, unknown> = {};
    ngZService.bindParagraphContext('same-paragraph', staleOutput);
    ngZService.setContextValue('privateValue', 'late-old-output', 'same-paragraph');
    expect(staleOutput.privateValue).toBeUndefined();
    const currentOutput: Record<string, unknown> = {};
    ngZService.bindParagraphContext('next-paragraph', currentOutput);
    ngZService.setContextValue('privateValue', 'current-paragraph', 'next-paragraph');
    expect(currentOutput.privateValue).toBe('current-paragraph');
  });

  it('preserves values on published NOTE broadcasts but clears missing models on an authoritative read', () => {
    const { component, ngZService } = setup();
    const data = snapshot('note-A', 'FINISHED', 'output');
    component.getNote({ op: OP.NOTE, data });
    component.angularObjectUpdate({
      noteId: 'note-A',
      interpreterGroupId: 'spark',
      angularObject: { name: 'controlValue', object: 'old-model', noteId: 'note-A' }
    });
    const context: Record<string, unknown> = {};
    ngZService.bindParagraphContext('same-paragraph', context);

    component.getNote({ op: OP.NOTE, data });
    expect(context.controlValue).toBe('old-model');
    component.getNote({ op: OP.NOTE, msgId: 'published-read', data });
    expect(context.controlValue).toBeUndefined();
    component.angularObjectUpdate({
      noteId: 'note-A',
      interpreterGroupId: 'spark',
      angularObject: { name: 'controlValue', object: 'current-model', noteId: 'note-A' }
    });
    expect(context.controlValue).toBe('current-model');
  });
});

describe('Published React streaming output', () => {
  it.each(['append', 'update'])('refreshes React props after a streaming %s', operation => {
    const { component } = setup(true);
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'RUNNING', 'initial') });
    const initialProps = component.reactProps;
    if (operation === 'append') {
      append(component, 'note-A', ' tail');
    } else {
      component.onParagraphUpdateOutput({
        noteId: 'note-A',
        paragraphId: 'same-paragraph',
        index: 0,
        type: DatasetType.TEXT,
        data: 'replacement'
      });
    }
    const expected = [{ type: DatasetType.TEXT, data: operation === 'append' ? 'initial tail' : 'replacement' }];
    expect(component.results).toEqual(expected);
    expect(component.reactProps.results).toEqual(expected);
    expect(component.reactProps).not.toBe(initialProps);
  });

  it('continues updating Angular results after React falls back', () => {
    const { component } = setup(true);
    component.reactFailed = true;
    const updateResult = vi.fn();
    component.notebookParagraphResultComponents.reset([{ updateResult } as never]);
    component.getNote({ op: OP.NOTE, data: snapshot('note-A', 'RUNNING', 'initial') });
    append(component, 'note-A', ' tail');
    expect(updateResult).toHaveBeenCalledWith(expect.anything(), { type: DatasetType.TEXT, data: 'initial tail' });
  });
});
