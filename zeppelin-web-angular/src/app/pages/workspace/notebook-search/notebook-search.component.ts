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
import { NotebookSearchResultItem } from '@zeppelin/interfaces';
import { NotebookService } from '@zeppelin/services';
import { Subject } from 'rxjs';
import { filter, map, switchMap, takeUntil, tap } from 'rxjs/operators';

@Component({
  selector: 'zeppelin-notebook-search',
  templateUrl: './notebook-search.component.html',
  styleUrls: ['./notebook-search.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookSearchComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  private searchAction$ = this.router.params.pipe(
    takeUntil(this.destroy$),
    map(params => params.queryStr),
    filter(queryStr => typeof queryStr === 'string' && !!queryStr.trim()),
    tap(queryStr => {
      this.searching = true;
      this.searchTerm = queryStr;
    }),
    switchMap(queryStr => this.notebookService.search(queryStr))
  );

  results: NotebookSearchResultItem[] = [];
  searching = false;
  searchTerm = '';

  get hasNoResults(): boolean {
    return !this.searching && this.results.length === 0 && this.searchTerm.length > 0;
  }

  constructor(
    private cdr: ChangeDetectorRef,
    private router: ActivatedRoute,
    private notebookService: NotebookService
  ) {}

  ngOnInit() {
    this.searchAction$.subscribe(results => {
      this.results = results;
      this.searching = false;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.notebookService.clearQuery();
    this.destroy$.next();
    this.destroy$.complete();
  }
}
