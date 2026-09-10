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
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd/message';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { NotebookRepo, NotebookRepoPutData } from '@zeppelin/interfaces';
import { NotebookRepoService, ReactFeatureService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-notebook-repos',
  templateUrl: './notebook-repos.component.html',
  styleUrls: ['./notebook-repos.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false
})
export class NotebookReposComponent implements OnInit, OnDestroy {
  repositories: NotebookRepo[] = [];
  useReactList = false;
  // Sticky for the component's lifetime: toggling the flag off and back on doesn't retry the
  // remote, even though the live queryParamMap subscription below re-evaluates `useReactList` on
  // every navigation. Matches the other React surfaces (configuration, published/paragraph,
  // notebook/paragraph), which are sticky the same way.
  reactListFailed = false;

  private destroy$ = new Subject<void>();
  private lastReactListProps: Record<string, unknown> | null = null;

  constructor(
    private notebookRepoService: NotebookRepoService,
    private activatedRoute: ActivatedRoute,
    private reactFeature: ReactFeatureService,
    private cdr: ChangeDetectorRef,
    private nzMessageService: NzMessageService
  ) {}

  get shouldUseReactList(): boolean {
    return this.useReactList && !this.reactListFailed;
  }

  // Memoized on repositories, the only input that changes. An object literal in
  // the template would hand ReactMountDirective a new identity on every
  // change-detection pass and make it call handle.update() each time.
  get reactListProps(): Record<string, unknown> {
    if (this.lastReactListProps?.repositories !== this.repositories) {
      this.lastReactListProps = {
        repositories: this.repositories,
        onRepoChange: this.onReactRepoChange,
        onError: this.onReactListError
      };
    }
    return this.lastReactListProps;
  }

  readonly onReactRepoChange = (repo: NotebookRepo): void => {
    this.updateRepoSetting(repo);
  };

  readonly onReactListError = (error: unknown): void => {
    console.error('React notebook repository list error', error);
    this.reactListFailed = true;
    this.cdr.markForCheck();
  };

  ngOnInit() {
    // Subscribed rather than read once: navigating between /notebook-repos and
    // /notebook-repos?reactNotebookRepos reuses this component, so a snapshot
    // read would keep the flag it saw first.
    this.activatedRoute.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.useReactList = this.reactFeature.isEnabled('notebookRepoList', params);
      this.cdr.markForCheck();
    });
    this.getRepos();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getRepos() {
    this.notebookRepoService
      .getRepos()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: data => {
          this.repositories = data.sort((a, b) => a.name.charCodeAt(0) - b.name.charCodeAt(0));
          this.cdr.markForCheck();
        },
        // This also runs after a failed PUT (updateRepoSetting refetches on error too), so a
        // silent failure here would leave the React card's pendingSave flag stuck forever, since
        // `repositories` keeps its old reference, reactListProps never rebuilds, and the [repo]
        // effect on the React side never re-fires. The message is the only recovery signal the
        // user gets in that case.
        error: error => {
          console.error('Failed to fetch notebook repositories', error);
          this.nzMessageService.error('Failed to load notebook repositories. Please try again.');
        }
      });
  }

  updateRepoSetting(repo: NotebookRepo) {
    const data: NotebookRepoPutData = {
      name: repo.className,
      settings: {}
    };
    repo.settings.forEach(({ name, selected }) => {
      data.settings[name] = selected;
    });

    this.notebookRepoService
      .updateRepo(data)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.getRepos(),
        // Refetch on failure too, so the card isn't stuck showing an unpersisted value.
        error: error => {
          console.error('Failed to update notebook repository', error);
          this.nzMessageService.error('Failed to save notebook repository settings. Please try again.');
          this.getRepos();
        }
      });
  }
}
