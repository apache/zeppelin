import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NzModule } from './ng-zorro-antd-module';

@NgModule({
  declarations: [],
  exports: [CommonModule, FormsModule, NzModule]
})
export class RuntimeDynamicModuleModule {}
