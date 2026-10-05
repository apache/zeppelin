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

import { ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd/message';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { NotebookRepo } from '@zeppelin/interfaces';
import { NotebookRepoService, ReactFeatureService } from '@zeppelin/services';

import { NotebookReposComponent } from './notebook-repos.component';

const repo = (overrides: Partial<NotebookRepo> = {}): NotebookRepo => ({
  name: 'GitNotebookRepo',
  className: 'org.apache.zeppelin.notebook.repo.GitNotebookRepo',
  settings: [{ type: 'INPUT', value: [], selected: '/opt/zeppelin/notebook', name: 'Notebook Path' }],
  ...overrides
});

const createComponent = (
  notebookRepoService: Partial<NotebookRepoService>,
  overrides: { activatedRoute?: ActivatedRoute; reactFeature?: Partial<ReactFeatureService> } = {}
) => {
  const cdr = { markForCheck: vi.fn() } as unknown as ChangeDetectorRef;
  const nzMessageService = { error: vi.fn() } as unknown as NzMessageService;
  const activatedRoute =
    overrides.activatedRoute ?? ({ queryParamMap: of({ get: () => null }) } as unknown as ActivatedRoute);
  const reactFeature = (overrides.reactFeature ?? { isEnabled: () => false }) as unknown as ReactFeatureService;
  const component = new NotebookReposComponent(
    notebookRepoService as NotebookRepoService,
    activatedRoute,
    reactFeature,
    cdr,
    nzMessageService
  );
  return { component, cdr, nzMessageService };
};

describe('NotebookReposComponent', () => {
  it('sorts and stores the repositories on a successful fetch', () => {
    const data = [repo({ name: 'ZNotebookRepo', className: 'Z' }), repo({ name: 'ANotebookRepo', className: 'A' })];
    const { component, cdr } = createComponent({ getRepos: () => of(data) });

    component.getRepos();

    expect(component.repositories.map(r => r.name)).toEqual(['ANotebookRepo', 'ZNotebookRepo']);
    expect(cdr.markForCheck).toHaveBeenCalled();
  });

  it('surfaces a message and leaves `repositories` untouched when the fetch fails', () => {
    const { component, nzMessageService } = createComponent({ getRepos: () => throwError(() => new Error('boom')) });
    const before = component.repositories;

    component.getRepos();

    expect(nzMessageService.error).toHaveBeenCalledTimes(1);
    // Same reference: reactListProps memoizes on this identity, so a silent failure here would
    // otherwise leave the React card's pendingSave stuck.
    expect(component.repositories).toBe(before);
  });

  it('refetches after a successful save', () => {
    const getRepos = vi.fn(() => of([]));
    const { component } = createComponent({ getRepos, updateRepo: () => of({}) });

    component.updateRepoSetting(repo());

    expect(getRepos).toHaveBeenCalledTimes(1);
  });

  it('surfaces a message and still refetches when a save fails', () => {
    const getRepos = vi.fn(() => of([]));
    const { component, nzMessageService } = createComponent({
      getRepos,
      updateRepo: () => throwError(() => new Error('boom'))
    });

    component.updateRepoSetting(repo());

    expect(nzMessageService.error).toHaveBeenCalledTimes(1);
    expect(getRepos).toHaveBeenCalledTimes(1);
  });

  it('sends the edited repo settings as a flat name/value map', () => {
    const updateRepo = vi.fn(() => of({}));
    const { component } = createComponent({ getRepos: () => of([]), updateRepo });

    component.updateRepoSetting(
      repo({
        settings: [
          { type: 'INPUT', value: [], selected: '/srv/notebook', name: 'Notebook Path' },
          { type: 'INPUT', value: [], selected: 'true', name: 'One Way Sync' }
        ]
      })
    );

    expect(updateRepo).toHaveBeenCalledWith({
      name: repo().className,
      settings: { 'Notebook Path': '/srv/notebook', 'One Way Sync': 'true' }
    });
  });

  it('ignores an in-flight fetch that resolves after the component is destroyed', () => {
    const response$ = new Subject<NotebookRepo[]>();
    const { component } = createComponent({ getRepos: () => response$ });

    component.getRepos();
    component.ngOnDestroy();
    response$.next([repo()]);

    // takeUntil(destroy$) tears down the subscription when the component is destroyed, so a
    // response that arrives afterward is dropped. Without it, `repositories` would pick up the
    // emit below.
    expect(component.repositories).toEqual([]);
  });

  it('memoizes reactListProps by identity so ReactMountDirective only calls update() when repositories actually changes', () => {
    const { component } = createComponent({ getRepos: () => of([repo()]) });
    component.getRepos();

    const first = component.reactListProps;
    const second = component.reactListProps;
    expect(second).toBe(first);

    // A second fetch, even of equivalent data, is a new `repositories` reference.
    component.getRepos();
    const third = component.reactListProps;
    expect(third).not.toBe(first);
    expect(third.repositories).toBe(component.repositories);
  });

  it('falls back to the Angular list once the React remote reports an error', () => {
    const { component, cdr } = createComponent({ getRepos: () => of([]) });
    component.useReactList = true;
    expect(component.shouldUseReactList).toBe(true);

    component.onReactListError(new Error('remote failed to load'));

    expect(component.reactListFailed).toBe(true);
    expect(component.shouldUseReactList).toBe(false);
    expect(cdr.markForCheck).toHaveBeenCalled();
  });

  it('re-evaluates the React flag on every queryParamMap emission, not just the one seen at construction', () => {
    const queryParamMap$ = new Subject<{ get: (key: string) => string | null }>();
    const isEnabled = vi.fn(
      (_surface: string, params: { get: (key: string) => string | null }) => params.get('reactNotebookRepos') !== null
    );
    const { component, cdr } = createComponent(
      { getRepos: () => of([]) },
      { activatedRoute: { queryParamMap: queryParamMap$ } as unknown as ActivatedRoute, reactFeature: { isEnabled } }
    );

    component.ngOnInit();
    queryParamMap$.next({ get: () => null });
    expect(component.useReactList).toBe(false);

    // A snapshot read at construction (or inside ngOnInit) would never see this second
    // navigation; only a live subscription picks up the flag turning on.
    queryParamMap$.next({ get: key => (key === 'reactNotebookRepos' ? 'true' : null) });
    expect(component.useReactList).toBe(true);
    expect(cdr.markForCheck).toHaveBeenCalled();
  });
});
