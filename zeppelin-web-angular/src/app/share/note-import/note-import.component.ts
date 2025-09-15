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
import { MessageService, TicketService } from '@zeppelin/services';

import { get } from 'lodash';
import { NzModalRef } from 'ng-zorro-antd/modal';
import { NzUploadFile } from 'ng-zorro-antd/upload';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { MessageReceiveDataTypeMap, OP, SendNote } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-note-import',
  templateUrl: './note-import.component.html',
  styleUrls: ['./note-import.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NoteImportComponent extends MessageListenersManager implements OnInit {
  noteImportName?: string;
  importUrl?: string;
  errorText?: string;
  importLoading = false;
  maxLimit = get(this.ticketService.configuration, ['zeppelin.websocket.max.text.message.size'], null);

  @MessageListener(OP.NOTES_INFO)
  getNotes(data: MessageReceiveDataTypeMap[OP.NOTES_INFO]) {
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
    if (file.size !== undefined && this.maxLimit && file.size > Number.parseInt(this.maxLimit, 10)) {
      this.errorText = 'File size limit Exceeded!';
    } else {
      const reader = new FileReader();
      // tslint:disable-next-line:no-any
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
      } catch (e) {
        this.errorText = 'JSON parse exception';
        return;
      }
    }
    // @ts-ignore
    if (result.paragraphs && result.paragraphs.length > 0) {
      if (!this.noteImportName) {
        // @ts-ignore
        this.noteImportName = result.name;
      } else {
        // @ts-ignore
        result.name = this.noteImportName;
      }
      this.messageService.importNote(result as SendNote);
    } else {
      this.errorText = 'Invalid JSON';
    }
    this.cdr.markForCheck();
  }

  constructor(
    public messageService: MessageService,
    private ticketService: TicketService,
    private cdr: ChangeDetectorRef,
    private nzModalRef: NzModalRef,
    private httpClient: HttpClient
  ) {
    super(messageService);
  }

  ngOnInit() {}
}
