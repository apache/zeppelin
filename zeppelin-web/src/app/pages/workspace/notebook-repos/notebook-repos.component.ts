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
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotebookRepo } from '@zeppelin/interfaces';
import { NotebookRepoService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-notebook-repos',
  templateUrl: './notebook-repos.component.html',
  styleUrls: ['./notebook-repos.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookReposComponent implements OnInit {
  repositories: NotebookRepo[] = [];

  constructor(private notebookRepoService: NotebookRepoService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.getRepos();
  }

  getRepos() {
    this.notebookRepoService.getRepos().subscribe(data => {
      this.repositories = data.sort((a, b) => a.name.charCodeAt(0) - b.name.charCodeAt(0));
      this.cdr.markForCheck();
    });
  }

  updateRepoSetting(repo: NotebookRepo) {
    const data = {
      name: repo.className,
      settings: {}
    };
    repo.settings.forEach(({ name, selected }) => {
      data.settings[name] = selected;
    });

    this.notebookRepoService.updateRepo(data).subscribe(() => {
      this.getRepos();
    });
  }
}
