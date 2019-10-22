import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { InterpreterComponent } from './interpreter.component';

const routes: Routes = [
  {
    path: '',
    component: InterpreterComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class InterpreterRoutingModule {}
