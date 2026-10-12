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

import { Component, Input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { MessageReceiveDataTypeMap, OP, WebSocketMessage } from '@zeppelin/sdk';

import { Permissions } from '@zeppelin/interfaces';
import { BaseUrlService } from '@zeppelin/services/base-url.service';
import { MessageService } from '@zeppelin/services/message.service';
import { SecurityService } from '@zeppelin/services/security.service';
import { TicketService } from '@zeppelin/services/ticket.service';

import { NotebookCoreReadComponent } from './notebook-core-read.component';
import template from './notebook-core-read.component.html?raw';

@Component({
  selector: 'zeppelin-notebook-paragraph-result',
  standalone: false,
  template: '{{ result.type }}:{{ result.data }}'
})
class ResultStubComponent {
  @Input() id!: string;
  @Input() result!: { type: string; data: string };
  @Input() config: unknown;
  @Input() published = false;
  @Input() isPending = false;
}

const setup = () => {
  const messageService = new MessageService({} as BaseUrlService, {} as TicketService, null as never);
  messageService.connectedStatus = true;
  const sent: Array<{ op: string; msgId: string }> = [];
  vi.spyOn(messageService, 'send').mockImplementation(((op: OP) => {
    const msgId = `msg-${sent.length + 1}`;
    sent.push({ op, msgId });
    return msgId;
  }) as MessageService['send']);
  const permissions = new Subject<Permissions>();
  const params = new BehaviorSubject<Params>({ noteId: 'A' });

  TestBed.configureTestingModule({
    declarations: [NotebookCoreReadComponent, ResultStubComponent],
    providers: [
      { provide: MessageService, useValue: messageService },
      { provide: SecurityService, useValue: { getPermissions: () => permissions } },
      { provide: TicketService, useValue: { ticket: { principal: 'alice', roles: '[]' } } },
      { provide: ActivatedRoute, useValue: { params } }
    ]
  }).overrideComponent(NotebookCoreReadComponent, {
    set: { template, templateUrl: undefined, styles: [], styleUrls: [] }
  });
  const fixture = TestBed.createComponent(NotebookCoreReadComponent);
  fixture.autoDetectChanges();

  const reply = (message: WebSocketMessage<MessageReceiveDataTypeMap>) => messageService.shortCircuit(message);
  const lastMsgId = () => sent[sent.length - 1].msgId;
  const root = () => fixture.nativeElement.querySelector('[data-testid="notebook-core-read"]') as HTMLElement;
  return { fixture, messageService, sent, permissions, params, reply, lastMsgId, root };
};

const noteMessage = (noteId: string, msgId: string) =>
  ({
    op: OP.NOTE,
    msgId,
    data: {
      note: {
        id: noteId,
        name: `${noteId} name`,
        paragraphs: [
          { id: 'p1', text: '%md one', status: 'FINISHED', results: { msg: [{ type: 'HTML', data: '<b>one</b>' }] } },
          { id: 'p2', text: '%angular', status: 'FINISHED', results: { msg: [{ type: 'ANGULAR', data: '<x>' }] } }
        ]
      }
    }
  }) as WebSocketMessage<MessageReceiveDataTypeMap>;

describe('NotebookCoreReadComponent', () => {
  it('sends only the note read when it mounts', () => {
    const { sent, root } = setup();

    expect(sent.map(({ op }) => op)).toEqual([OP.GET_NOTE]);
    expect(root().dataset.status).toBe('loading');
  });

  it('renders the loaded note when its reply arrives', async () => {
    const { fixture, reply, lastMsgId, permissions, root } = setup();

    reply(noteMessage('A', lastMsgId()));
    // An empty list admits everyone, so the other lists name someone else.
    permissions.next({ owners: ['bob'], readers: ['alice'], runners: ['bob'], writers: ['bob'] });
    await fixture.whenStable();

    expect(root().dataset.status).toBe('ready');
    expect(root().dataset.aclStatus).toBe('ready');
    const paragraphs = root().querySelectorAll('[data-testid="notebook-core-read-paragraph"]');
    expect([...paragraphs].map(p => (p as HTMLElement).dataset.paragraphId)).toEqual(['p1', 'p2']);
    expect(root().querySelector('[data-testid="notebook-core-read-result"]')!.textContent).toBe('HTML:<b>one</b>');
    expect(
      (root().querySelector('[data-testid="notebook-core-read-unsupported-result"]') as HTMLElement).dataset.resultType
    ).toBe('ANGULAR');
    expect(root().querySelector('[data-testid="notebook-core-read-permissions"]')!.textContent).toContain('reader');
  });

  it('reads a revision and then the live note as the route changes, sending reads only', () => {
    const { sent, params } = setup();

    params.next({ noteId: 'A', revisionId: 'r1' });
    params.next({ noteId: 'B' });

    expect(sent.map(({ op }) => op)).toEqual([OP.GET_NOTE, OP.NOTE_REVISION, OP.GET_NOTE]);
  });

  it('disposes its Core once on unmount and sends nothing while doing so', () => {
    const { fixture, sent, reply, lastMsgId, messageService } = setup();
    reply(noteMessage('A', lastMsgId()));
    const { host } = fixture.componentInstance;
    const dispose = vi.spyOn(host, 'dispose');
    const sentBefore = sent.length;

    fixture.destroy();
    messageService.connectedStatus$.next(false);
    messageService.connectedStatus$.next(true);

    expect(dispose).toHaveBeenCalledTimes(1);
    expect(host.port.getSnapshot().status).toBe('disposed');
    expect(sent).toHaveLength(sentBefore);
  });
});
