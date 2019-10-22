import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { NotebookComponent } from './notebook.component';

const routes: Routes = [
  {
    path: ':noteId',
    component: NotebookComponent
  },
  {
    path: ':noteId/paragraph/:paragraphId',
    component: NotebookComponent
  },
  {
    path: ':noteId/revision/:revisionId',
    component: NotebookComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class NotebookRoutingModule {}
