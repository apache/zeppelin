import { moveItemInArray, CdkDragDrop } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';

import { NzModalService } from 'ng-zorro-antd';

import { InterpreterBindingItem } from '@zeppelin/sdk';
import { InterpreterService, MessageService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-notebook-interpreter-binding',
  templateUrl: './interpreter-binding.component.html',
  styleUrls: ['./interpreter-binding.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookInterpreterBindingComponent {
  private restarting = false;
  @Input() noteId: string;
  @Input() interpreterBindings: InterpreterBindingItem[] = [];
  @Input() activatedExtension: 'interpreter' | 'permissions' | 'revisions' | 'hide' = 'hide';
  @Output() readonly activatedExtensionChange = new EventEmitter<
    'interpreter' | 'permissions' | 'revisions' | 'hide'
  >();

  restartInterpreter(interpreter: InterpreterBindingItem) {
    this.nzModalService.create({
      nzTitle: 'Restart interpreter',
      nzContent: `Do you want to restart ${interpreter.name} interpreter?`,
      nzOkLoading: this.restarting,
      nzOnOk: () =>
        new Promise(resolve => {
          this.restarting = true;
          this.interpreterService.restartInterpreter(interpreter.id, this.noteId).subscribe(
            data => {
              this.restarting = false;
              this.cdr.markForCheck();
              resolve();
            },
            () => {
              this.restarting = false;
              resolve();
            }
          );
        })
    });
  }

  drop(event: CdkDragDrop<InterpreterBindingItem[]>) {
    moveItemInArray(this.interpreterBindings, event.previousIndex, event.currentIndex);
  }

  saveSetting() {
    const selectedSettingIds = this.interpreterBindings.filter(item => item.selected).map(item => item.id);
    this.messageService.saveInterpreterBindings(this.noteId, selectedSettingIds);
    this.messageService.getInterpreterBindings(this.noteId);
    this.closeSetting();
  }

  closeSetting() {
    this.activatedExtension = 'hide';
    this.activatedExtensionChange.emit('hide');
  }

  constructor(
    private nzModalService: NzModalService,
    private interpreterService: InterpreterService,
    private cdr: ChangeDetectorRef,
    private messageService: MessageService
  ) {}
}
