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
        path: 'notebook/:noteId/paragraph',
        loadChildren: () => import('@zeppelin/pages/workspace/published/published.module').then(m => m.PublishedModule)
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
