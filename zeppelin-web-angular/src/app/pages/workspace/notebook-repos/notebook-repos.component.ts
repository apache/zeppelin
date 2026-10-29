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
import { EMPTY, firstValueFrom, merge, of, Subject } from 'rxjs';
import { catchError, filter, map, switchMap, take, takeUntil } from 'rxjs/operators';
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

  private nextFetchVersion = 0;
  private snapshot = { repositories: this.repositories, version: 0 };
  private readonly snapshots$ = new Subject<typeof this.snapshot>();
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

  // Keep props stable until the server snapshot changes.
  get reactListProps(): Record<string, unknown> {
    if (this.lastReactListProps?.snapshot !== this.snapshot) {
      this.lastReactListProps = {
        snapshot: this.snapshot,
        onSave: this.onReactRepoSave,
        onError: this.onReactListError
      };
    }
    return this.lastReactListProps;
  }

  readonly onReactRepoSave = (repo: NotebookRepo) => firstValueFrom(this.saveRepo(repo));

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
    this.snapshots$.complete();
  }

  getRepos(): void {
    this.fetchRepos().subscribe();
  }

  updateRepoSetting(repo: NotebookRepo): void {
    this.saveRepo(repo).subscribe();
  }

  private fetchRepos() {
    const version = ++this.nextFetchVersion;
    return this.notebookRepoService.getRepos().pipe(
      map(data => {
        if (version > this.snapshot.version) {
          this.repositories = data.sort((a, b) => a.name.charCodeAt(0) - b.name.charCodeAt(0));
          this.snapshot = { repositories: this.repositories, version };
          this.cdr.markForCheck();
          this.snapshots$.next(this.snapshot);
        }
        return this.snapshot;
      }),
      catchError(error => {
        console.error('Failed to fetch notebook repositories', error);
        this.nzMessageService.error('Failed to load notebook repositories. Please try again.');
        return EMPTY;
      }),
      takeUntil(this.destroy$)
    );
  }

  private saveRepo(repo: NotebookRepo) {
    const data: NotebookRepoPutData = {
      name: repo.className,
      settings: {}
    };
    repo.settings.forEach(({ name, selected }) => {
      data.settings[name] = selected;
    });

    return this.notebookRepoService.updateRepo(data).pipe(
      catchError(error => {
        console.error('Failed to update notebook repository', error);
        this.nzMessageService.error('Failed to save notebook repository settings. Please try again.');
        return of(null);
      }),
      switchMap(() => {
        const version = this.nextFetchVersion + 1;
        // Subscribe before starting the GET, including for synchronous responses.
        // If it fails, a later GET can still confirm this request without keeping completed-save history.
        return merge(this.snapshots$.pipe(filter(snapshot => snapshot.version >= version)), this.fetchRepos()).pipe(
          take(1)
        );
      }),
      takeUntil(this.destroy$)
    );
  }
}
