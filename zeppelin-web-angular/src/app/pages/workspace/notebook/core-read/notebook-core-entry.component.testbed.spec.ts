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

import { Component, Directive, Input, OnDestroy } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { map } from 'rxjs/operators';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { OP } from '@zeppelin/sdk';

import { Permissions } from '@zeppelin/interfaces';
import { BaseUrlService } from '@zeppelin/services/base-url.service';
import { MessageService } from '@zeppelin/services/message.service';
import { SecurityService } from '@zeppelin/services/security.service';
import { TicketService } from '@zeppelin/services/ticket.service';

import { NotebookCoreEntryComponent } from './notebook-core-entry.component';
import { NotebookCoreReadHost } from './notebook-core-read-host';

type SkeletonProps = Readonly<{
  core: unknown;
  anchor: string | null;
  failure: string | null;
  onMounted: () => void;
  onError: (error: unknown) => void;
}>;

// Stands in for ReactMountDirective: records what the entry hands the remote and when it is torn down.
const mounts: Array<{ module: string; props: () => SkeletonProps; destroyed: boolean }> = [];

@Directive({ selector: '[zeppelin-react-mount]', standalone: false })
class ReactMountStubDirective implements OnDestroy {
  @Input() reactProps: Record<string, unknown> = {};
  private readonly mount = { module: '', props: () => this.reactProps as SkeletonProps, destroyed: false };

  constructor() {
    mounts.push(this.mount);
  }

  @Input('zeppelin-react-mount') set module(module: string) {
    this.mount.module = module;
  }

  ngOnDestroy(): void {
    this.mount.destroyed = true;
  }
}

@Component({ selector: 'zeppelin-notebook-core-read', standalone: false, template: '' })
class ReadViewStubComponent {
  @Input() host!: NotebookCoreReadHost;
}

const setup = (query: Params = {}) => {
  const messageService = new MessageService({} as BaseUrlService, {} as TicketService, null as never);
  messageService.connectedStatus = true;
  const sent: OP[] = [];
  vi.spyOn(messageService, 'send').mockImplementation(((op: OP) => {
    sent.push(op);
    return `msg-${sent.length}`;
  }) as MessageService['send']);
  const params = new BehaviorSubject<Params>({ noteId: 'A' });
  const queryParams = new BehaviorSubject<Params>(query);

  TestBed.configureTestingModule({
    declarations: [NotebookCoreEntryComponent, ReactMountStubDirective, ReadViewStubComponent],
    providers: [
      { provide: MessageService, useValue: messageService },
      { provide: SecurityService, useValue: { getPermissions: () => new Subject<Permissions>() } },
      {
        provide: ActivatedRoute,
        useValue: {
          params,
          queryParamMap: queryParams.pipe(map(convertToParamMap)),
          snapshot: { queryParamMap: convertToParamMap(query) }
        }
      }
    ]
  });
  const fixture = TestBed.createComponent(NotebookCoreEntryComponent);
  fixture.autoDetectChanges();

  const { host } = fixture.componentInstance;
  const root = () => fixture.nativeElement.querySelector('[data-testid="notebook-core-entry"]') as HTMLElement;
  const readView = () => fixture.debugElement.query(el => el.componentInstance instanceof ReadViewStubComponent);
  const mount = () => mounts[mounts.length - 1];
  return { fixture, host, sent, params, queryParams, root, readView, mount };
};

const REACT = { notebookCoreRead: '', notebookCoreReact: '' };

afterEach(() => {
  mounts.length = 0;
  vi.restoreAllMocks();
});

describe('NotebookCoreEntryComponent', () => {
  it('shows the Angular read view on its host without the React flag, ignoring the failure parameter', () => {
    const { host, sent, root, readView } = setup({ notebookCoreRead: '', notebookCoreReactFailure: 'mount' });

    expect(root().dataset.renderer).toBe('angular');
    expect(readView().componentInstance.host).toBe(host);
    expect(mounts).toHaveLength(0);
    expect(sent).toEqual([OP.GET_NOTE]);
  });

  it('mounts the React read view on the host port with the paragraph anchor', () => {
    const { fixture, host, queryParams, root, readView, mount } = setup({ ...REACT, paragraph: 'p2' });

    expect(root().dataset.renderer).toBe('react-loading');
    expect(readView()).toBeNull();
    expect(mount().module).toBe('./NotebookReadSkeleton');
    expect(mount().props().core).toBe(host.port);
    expect(mount().props().anchor).toBe('p2');
    expect(mount().props().failure).toBeNull();

    queryParams.next({ ...REACT, paragraph: 'p3' });
    fixture.detectChanges();

    expect(mount().props().core).toBe(host.port);
    expect(mount().props().anchor).toBe('p3');
    expect(mounts).toHaveLength(1);
  });

  it('passes a known failure parameter to the React read view and drops an unknown one', () => {
    expect(
      setup({ ...REACT, notebookCoreReactFailure: 'after-mount' })
        .mount()
        .props().failure
    ).toBe('after-mount');
    TestBed.resetTestingModule();
    expect(
      setup({ ...REACT, notebookCoreReactFailure: 'other' })
        .mount()
        .props().failure
    ).toBeNull();
  });

  it('falls back to the Angular read view on the same host when React fails before its first commit', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { fixture, host, sent, root, readView, mount } = setup(REACT);
    const reactMount = mount();

    reactMount.props().onError(new Error('remote failed'));
    fixture.detectChanges();

    expect(root().dataset.renderer).toBe('angular-fallback');
    expect(reactMount.destroyed).toBe(true);
    expect(readView().componentInstance.host).toBe(host);
    expect(host.port.getSnapshot().status).toBe('loading');
    expect(sent).toEqual([OP.GET_NOTE]);
  });

  it('reports a failure after the first commit without switching to Angular', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { fixture, root, readView, mount } = setup(REACT);
    const reactMount = mount();

    reactMount.props().onMounted();
    fixture.detectChanges();
    expect(root().dataset.renderer).toBe('react-mounted');

    reactMount.props().onError(new Error('render failed'));
    fixture.detectChanges();

    expect(root().dataset.renderer).toBe('runtime-error');
    expect(reactMount.destroyed).toBe(false);
    expect(readView()).toBeNull();
    expect(console.error).toHaveBeenCalledTimes(1);
  });

  it('opens the note or revision from the route on the same host, sending reads only', () => {
    const { host, sent, params } = setup(REACT);
    const port = host.port;

    params.next({ noteId: 'A', revisionId: 'r1' });
    params.next({ noteId: 'B' });

    expect(sent).toEqual([OP.GET_NOTE, OP.NOTE_REVISION, OP.GET_NOTE]);
    expect(host.port).toBe(port);
    expect(host.port.getSnapshot().noteId).toBe('B');
  });

  it('disposes its host once on destroy and sends nothing while doing so', () => {
    const { fixture, host, sent } = setup(REACT);
    const dispose = vi.spyOn(host, 'dispose');

    fixture.destroy();

    expect(dispose).toHaveBeenCalledTimes(1);
    expect(host.port.getSnapshot().status).toBe('disposed');
    expect(sent).toEqual([OP.GET_NOTE]);
  });
});
