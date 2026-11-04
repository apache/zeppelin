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

import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { selectAclCapabilities, selectOrderedParagraphs, NotebookReadSnapshot } from '@zeppelin/notebook-core';
import { TicketService } from '@zeppelin/services';

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
 * unmount. The route entry owns the host: this view neither opens nor disposes it.
 */
@Component({
  selector: 'zeppelin-notebook-core-read',
  standalone: false,
  templateUrl: './notebook-core-read.component.html',
  styleUrls: ['./notebook-core-read.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookCoreReadComponent implements OnInit {
  @Input({ required: true }) host!: NotebookCoreReadHost;
  view$!: Observable<ReadView>;

  constructor(private readonly ticketService: TicketService) {}

  ngOnInit(): void {
    this.view$ = this.host.state$.pipe(
      map(({ snapshot, connected }) => ({
        snapshot,
        connected,
        paragraphs: selectOrderedParagraphs(snapshot).map(toReadParagraphView),
        access: toReadAccess(selectAclCapabilities(snapshot, toPrincipals(this.ticketService.ticket)))
      }))
    );
  }

  retry(): void {
    this.host.retry();
  }
}
