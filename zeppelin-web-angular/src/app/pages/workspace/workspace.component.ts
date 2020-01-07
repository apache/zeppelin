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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { publishedSymbol } from '@zeppelin/core/paragraph-base/published';
import { HeliumManagerService } from '@zeppelin/helium-manager';
import { MessageService } from '@zeppelin/services';
import { setTheme } from '@zeppelin/visualizations/g2.config';
import { NzMessageService } from 'ng-zorro-antd/message';

@Component({
  selector: 'zeppelin-workspace',
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  private messageId = null;
  publishMode = false;

  constructor(
    public messageService: MessageService,
    private cdr: ChangeDetectorRef,
    private nzMessageService: NzMessageService,
    private heliumManagerService: HeliumManagerService
  ) {}

  onActivate(component) {
    this.publishMode = component && component[publishedSymbol];
    this.cdr.markForCheck();
  }

  /**
   * Close the old connection manually when the network is offline
   * and connect a new, the {@link MessageService} will auto-retry
   */
  @HostListener('window:offline')
  onOffline() {
    this.messageService.close();
    this.messageService.connect();
  }

  setUpWebsocketReconnectMessage() {
    this.messageService.connectedStatus$.pipe(takeUntil(this.destroy$)).subscribe(data => {
      if (!data) {
        if (this.messageId === null) {
          this.messageId = this.nzMessageService.loading('Connecting WebSocket ...', { nzDuration: 0 }).messageId;
        }
      } else {
        this.nzMessageService.remove(this.messageId);
        this.messageId = null;
      }
      this.cdr.markForCheck();
    });
  }

  ngOnInit() {
    setTheme();
    this.setUpWebsocketReconnectMessage();
    this.heliumManagerService.initPackages();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
