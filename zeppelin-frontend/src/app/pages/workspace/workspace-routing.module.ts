import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { WorkspaceComponent } from './workspace.component';
import { WorkspaceGuard } from './workspace.guard';

const routes: Routes = [
  {
    path: '',
    component: WorkspaceComponent,
    canActivate: [WorkspaceGuard],
    children: [
      {
        path: '',
        loadChildren: () => import('@zeppelin/pages/workspace/home/home.module').then(m => m.HomeModule)
      },
      {
        path: 'notebook',
        loadChildren: () => import('@zeppelin/pages/workspace/notebook/notebook.module').then(m => m.NotebookModule)
      },
      {
        path: 'jobmanager',
        loadChildren: () =>
          import('@zeppelin/pages/workspace/job-manager/job-manager.module').then(m => m.JobManagerModule)
      },
      {
        path: 'interpreter',
        loadChildren: () =>
          import('@zeppelin/pages/workspace/interpreter/interpreter.module').then(m => m.InterpreterModule)
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class WorkspaceRoutingModule {}
