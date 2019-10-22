import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';

import { CodeEditorComponent } from './code-editor.component';

@NgModule({
  declarations: [CodeEditorComponent],
  imports: [CommonModule, NzIconModule, NzSpinModule],
  exports: [CodeEditorComponent]
})
export class CodeEditorModule {}
