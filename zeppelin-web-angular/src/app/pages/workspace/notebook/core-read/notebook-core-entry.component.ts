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
import { ActivatedRoute, ParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MessageService, ReactFeatureService, SecurityService } from '@zeppelin/services';

import { NotebookCoreReadHost } from './notebook-core-read-host';

/**
 * Which view the private read route shows. The `react-*` values and `runtime-error` describe the
 * React mount only, never whether the note is loaded.
 */
export type NotebookCoreRenderer = 'angular' | 'react-loading' | 'react-mounted' | 'runtime-error' | 'angular-fallback';

/** Test-only: makes the React read view throw, so the fallback can be exercised. */
const NOTEBOOK_CORE_REACT_FAILURE_QUERY_PARAM = 'notebookCoreReactFailure';
const NOTEBOOK_CORE_REACT_FAILURES = ['mount', 'after-mount'] as const;
type NotebookCoreReactFailure = (typeof NOTEBOOK_CORE_REACT_FAILURES)[number];

const toFailure = (value: string | null): NotebookCoreReactFailure | null =>
  NOTEBOOK_CORE_REACT_FAILURES.find(failure => failure === value) ?? null;

/**
 * The private read route (ZEPPELIN-6727). It owns one host for the route lifetime and, with
 * `notebookCoreReact`, shows the React read view on that host's port. The renderer is chosen at
 * entry: a React failure before the first commit falls back to the Angular read view on the same
 * host, a failure after it is only reported.
 */
@Component({
  selector: 'zeppelin-notebook-core-entry',
  standalone: false,
  template: `
    <div data-testid="notebook-core-entry" [attr.data-renderer]="renderer">
      @if (renderer === 'angular' || renderer === 'angular-fallback') {
        <zeppelin-notebook-core-read [host]="host"></zeppelin-notebook-core-read>
      } @else {
        <div zeppelin-react-mount="./NotebookReadSkeleton" [reactProps]="reactProps"></div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookCoreEntryComponent implements OnInit, OnDestroy {
  readonly host: NotebookCoreReadHost;
  renderer: NotebookCoreRenderer;
  reactProps: Record<string, unknown> = {};
  private readonly failure: NotebookCoreReactFailure | null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly cdr: ChangeDetectorRef,
    messageService: MessageService,
    securityService: SecurityService,
    reactFeature: ReactFeatureService
  ) {
    this.host = new NotebookCoreReadHost(messageService, messageService, securityService);
    const query = activatedRoute.snapshot.queryParamMap;
    this.renderer = reactFeature.isEnabled('notebookReadReact', query) ? 'react-loading' : 'angular';
    this.failure = toFailure(query.get(NOTEBOOK_CORE_REACT_FAILURE_QUERY_PARAM));
  }

  ngOnInit(): void {
    this.activatedRoute.params.pipe(takeUntil(this.destroy$)).subscribe(({ noteId, revisionId }) => {
      if (!noteId) {
        throw new Error('Route parameter `noteId` is required.');
      }
      this.host.open(revisionId ? { kind: 'revision', noteId, revisionId } : { kind: 'note', noteId });
    });
    if (this.renderer === 'react-loading') {
      this.activatedRoute.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(query => this.setReactProps(query));
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.host.dispose();
  }

  private readonly onMounted = (): void => {
    if (this.renderer === 'react-loading') {
      this.renderer = 'react-mounted';
      this.cdr.markForCheck();
    }
  };

  private readonly onError = (error: unknown): void => {
    console.error('React notebook read view error', error);
    if (this.renderer === 'react-loading') {
      this.renderer = 'angular-fallback';
    } else if (this.renderer === 'react-mounted') {
      this.renderer = 'runtime-error';
    }
    this.cdr.markForCheck();
  };

  private setReactProps(query: ParamMap): void {
    this.reactProps = {
      core: this.host.port,
      anchor: query.get('paragraph'),
      failure: this.failure,
      onMounted: this.onMounted,
      onError: this.onError
    };
    this.cdr.markForCheck();
  }
}
