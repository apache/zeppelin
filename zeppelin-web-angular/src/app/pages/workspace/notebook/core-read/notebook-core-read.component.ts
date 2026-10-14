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

import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';

import { selectAclCapabilities, selectOrderedParagraphs, NotebookReadSnapshot } from '@zeppelin/notebook-core';
import { MessageService, SecurityService, TicketService } from '@zeppelin/services';

import { NotebookCoreReadHost } from './notebook-core-read-host';
import {
  toPrincipals,
  toReadAccess,
  toReadParagraphView,
  ReadAccess,
  ReadParagraphView
} from './notebook-core-read-view';

type ReadView = Readonly<{
  snapshot: NotebookReadSnapshot;
  connected: boolean;
  paragraphs: readonly ReadParagraphView[];
  access: ReadAccess;
}>;

/**
 * The private read-only notebook view. It renders loaded Core snapshots and saved results only:
 * nothing here sends run, save, patch, form or configuration messages, including on mount and
 * unmount. Its host, and the port on it, live exactly as long as this route component.
 */
@Component({
  selector: 'zeppelin-notebook-core-read',
  standalone: false,
  templateUrl: './notebook-core-read.component.html',
  styleUrls: ['./notebook-core-read.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookCoreReadComponent implements OnInit, OnDestroy {
  readonly host: NotebookCoreReadHost;
  readonly view$: Observable<ReadView>;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    messageService: MessageService,
    securityService: SecurityService,
    ticketService: TicketService
  ) {
    this.host = new NotebookCoreReadHost(messageService, messageService, securityService);
    this.view$ = this.host.state$.pipe(
      map(({ snapshot, connected }) => ({
        snapshot,
        connected,
        paragraphs: selectOrderedParagraphs(snapshot).map(toReadParagraphView),
        access: toReadAccess(selectAclCapabilities(snapshot, toPrincipals(ticketService.ticket)))
      }))
    );
  }

  ngOnInit(): void {
    this.activatedRoute.params.pipe(takeUntil(this.destroy$)).subscribe(({ noteId, revisionId }) => {
      if (!noteId) {
        throw new Error('Route parameter `noteId` is required.');
      }
      this.host.open(revisionId ? { kind: 'revision', noteId, revisionId } : { kind: 'note', noteId });
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.host.dispose();
  }

  retry(): void {
    this.host.retry();
  }
}
