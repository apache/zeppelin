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

import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ConfigurationService, MessageService, TicketService } from '@zeppelin/services';

import { NzModalRef } from 'ng-zorro-antd/modal';
import { NzUploadFile } from 'ng-zorro-antd/upload';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { ImportNote, MessageReceiveDataTypeMap, OP } from '@zeppelin/sdk';
import { isRecord } from '@zeppelin/utility';

const isImportNote = (value: unknown): value is ImportNote['note'] =>
  isRecord(value) && Array.isArray(value.paragraphs) && value.paragraphs.length > 0;

@Component({
  selector: 'zeppelin-note-import',
  templateUrl: './note-import.component.html',
  styleUrls: ['./note-import.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false
})
export class NoteImportComponent extends MessageListenersManager implements OnInit {
  noteImportName?: string;
  importUrl?: string;
  errorText?: string;
  importLoading = false;
  wsMaxLimit?: number;

  @MessageListener(OP.IMPORT_NOTE)
  noteImported(_: MessageReceiveDataTypeMap[OP.IMPORT_NOTE]) {
    this.nzModalRef.destroy();
  }

  importNote() {
    this.errorText = '';
    this.importLoading = true;
    this.httpClient.get(this.importUrl ?? '').subscribe(
      data => {
        this.importLoading = false;
        this.processImportJson(data);
        this.cdr.markForCheck();
      },
      () => {
        this.errorText = 'Unable to Fetch URL';
        this.importLoading = false;
        this.cdr.markForCheck();
      },
      () => {}
    );
  }

  beforeUpload = (file: NzUploadFile): boolean => {
    this.errorText = '';
    if (file.size !== undefined && this.wsMaxLimit && file.size > this.wsMaxLimit) {
      this.errorText = 'File size limit Exceeded!';
    } else {
      const reader = new FileReader();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      reader.readAsText(file as any);
      reader.onloadend = () => {
        this.processImportJson(reader.result);
      };
    }
    this.cdr.markForCheck();
    return false;
  };

  processImportJson(data: unknown) {
    let result = data;
    if (typeof result !== 'object') {
      try {
        result = JSON.parse(result as string);
      } catch {
        this.errorText = 'JSON parse exception';
        return;
      }
    }
    if (isImportNote(result)) {
      if (!this.noteImportName) {
        this.noteImportName = result.name;
      } else {
        result.name = this.noteImportName;
      }
      this.messageService.importNote(result);
    } else {
      this.errorText = 'Invalid JSON';
    }
    this.cdr.markForCheck();
  }

  constructor(
    public messageService: MessageService,
    private ticketService: TicketService,
    private configurationService: ConfigurationService,
    private cdr: ChangeDetectorRef,
    private nzModalRef: NzModalRef,
    private httpClient: HttpClient
  ) {
    super(messageService);
  }

  async ngOnInit() {
    this.wsMaxLimit = await this.configurationService.fetchWsMaxMessageSize();
  }
}
