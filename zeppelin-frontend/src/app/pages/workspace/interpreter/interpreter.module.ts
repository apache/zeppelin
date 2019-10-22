import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import {
  NzAlertModule,
  NzBadgeModule,
  NzButtonModule,
  NzCardModule,
  NzCheckboxModule,
  NzDividerModule,
  NzDropDownModule,
  NzFormModule,
  NzIconModule,
  NzInputModule,
  NzMessageModule,
  NzModalModule,
  NzRadioModule,
  NzSelectModule,
  NzSwitchModule,
  NzTableModule,
  NzTagModule,
  NzToolTipModule
} from 'ng-zorro-antd';

import { ShareModule } from '@zeppelin/share';

import { InterpreterCreateRepositoryModalComponent } from './create-repository-modal/create-repository-modal.component';
import { InterpreterRoutingModule } from './interpreter-routing.module';
import { InterpreterComponent } from './interpreter.component';
import { InterpreterItemComponent } from './item/item.component';

@NgModule({
  declarations: [InterpreterComponent, InterpreterCreateRepositoryModalComponent, InterpreterItemComponent],
  entryComponents: [InterpreterCreateRepositoryModalComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    InterpreterRoutingModule,
    ShareModule,
    NzFormModule,
    NzSelectModule,
    NzSwitchModule,
    NzToolTipModule,
    NzCheckboxModule,
    NzRadioModule,
    NzBadgeModule,
    NzButtonModule,
    NzModalModule,
    NzInputModule,
    NzDividerModule,
    NzTagModule,
    NzCardModule,
    NzDropDownModule,
    NzIconModule,
    NzTableModule,
    NzMessageModule,
    NzAlertModule
  ]
})
export class InterpreterModule {}
