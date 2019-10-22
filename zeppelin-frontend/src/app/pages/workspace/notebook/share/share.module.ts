import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd';

import { ElasticInputComponent } from './elastic-input/elastic-input.component';

@NgModule({
  declarations: [ElasticInputComponent],
  exports: [ElasticInputComponent],
  imports: [CommonModule, NzInputModule, FormsModule]
})
export class NotebookShareModule {}
