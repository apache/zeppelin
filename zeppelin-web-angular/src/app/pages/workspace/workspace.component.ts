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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { filter, map, startWith, takeUntil, tap } from 'rxjs/operators';

import { ActivatedRoute, NavigationEnd, Route, Router } from '@angular/router';
import { publishedSymbol, Published } from '@zeppelin/core/paragraph-base/published';
import { HeliumManagerService } from '@zeppelin/helium-manager';
import { MessageService } from '@zeppelin/services';
import { log } from 'ng-zorro-antd/core';

@Component({
  selector: 'zeppelin-workspace',
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  websocketConnected = false;
  publishMode = false;

  constructor(
    public messageService: MessageService,
    private cdr: ChangeDetectorRef,
    private heliumManagerService: HeliumManagerService
  ) {}

  onActivate(e) {
    this.publishMode = e && e[publishedSymbol];
    this.cdr.markForCheck();
  }

  ngOnInit() {
    this.messageService.connectedStatus$.pipe(takeUntil(this.destroy$)).subscribe(data => {
      this.websocketConnected = data;
      this.cdr.markForCheck();
    });
    this.heliumManagerService.initPackages();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
