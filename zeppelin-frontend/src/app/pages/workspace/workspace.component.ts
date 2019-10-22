import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { HeliumManagerService } from '@zeppelin/helium-manager';
import { MessageService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-workspace',
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  websocketConnected = false;

  constructor(
    public messageService: MessageService,
    private cdr: ChangeDetectorRef,
    private heliumManagerService: HeliumManagerService
  ) {}

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
