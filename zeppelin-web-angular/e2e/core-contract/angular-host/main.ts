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

import { CommonModule } from '@angular/common';
import { Component, Injectable, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterModule, RouterStateSnapshot } from '@angular/router';
import { TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';
import type { NotebookCorePort, NotebookCoreSnapshot } from '@zeppelin/notebook-core';
import { NotebookComponent } from '@zeppelin/pages/workspace/notebook/notebook.component';
import {
  NOTEBOOK_CHILD_ROUTE_PATHS,
  NOTEBOOK_ROUTE_PATH
} from '@zeppelin/pages/workspace/notebook/notebook-route-boundary';
import { WorkspaceGuard } from '@zeppelin/pages/workspace/workspace.guard';
import { MessageService, ReactFeatureService } from '@zeppelin/services';
import { HeliumService } from '@zeppelin/services/helium.service';
import { ThemeService } from '@zeppelin/services/theme.service';
import { TicketService } from '@zeppelin/services/ticket.service';
import { ShareModule } from '@zeppelin/share';
import { NzMessageService } from 'ng-zorro-antd/message';
import { BehaviorSubject, NEVER, filter } from 'rxjs';

declare global {
  interface Window {
    __zeppelinNotebookCorePortProof?: {
      hostCore: NotebookCorePort;
      proofs: unknown[];
      receivedCore?: NotebookCorePort;
    };
    __zeppelinNotebookRouteBoundaryProof?: {
      activatedProductionNotebookComponents: boolean[];
      connectionLifecycleCalls: string[];
      hostCore: NotebookCorePort;
      messageCalls: Array<{ method: string; noteId: string; revisionId?: string }>;
      receivedCore?: NotebookCorePort;
      receivedCores: NotebookCorePort[];
      routePaths: string[];
      workspaceGuardCalls: string[];
    };
  }
}

@Component({
  selector: 'zeppelin-notebook-core-port-proof-app',
  standalone: false,
  template: `
    <router-outlet></router-outlet>
    @if (notebookRouteActive) {
      <button type="button" data-testid="navigate-notebook-note" [routerLink]="['/notebook', 'note-route-updated']">
        navigate note
      </button>
      <button
        type="button"
        data-testid="navigate-notebook-revision"
        [routerLink]="['/notebook', 'note-route-updated', 'revision', 'revision-from-route']"
      >
        navigate revision
      </button>
      <div [zeppelin-react-mount]="'./NotebookCorePortProbe'" [reactProps]="routeReactProps"></div>
    }
  `
})
export class NotebookCorePortProofAppComponent {
  notebookRouteActive = false;
  readonly routeReactProps: Readonly<{
    core: NotebookCorePort;
    expectedCore: NotebookCorePort;
    onReceivedCore: (receivedCore: NotebookCorePort) => void;
  }>;

  constructor(router: Router, portHost: NotebookRouteBoundaryPortHost) {
    this.routeReactProps = {
      core: portHost.core,
      expectedCore: portHost.core,
      onReceivedCore: receivedCore => {
        const state = window.__zeppelinNotebookRouteBoundaryProof;
        if (state) {
          state.receivedCore = receivedCore;
          state.receivedCores.push(receivedCore);
        }
      }
    };
    router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe(() => {
      const notebookRoute = findActivatedNotebookRoute(router.routerState.snapshot.root);
      this.notebookRouteActive = notebookRoute !== undefined;
      if (notebookRoute) {
        window.__zeppelinNotebookRouteBoundaryProof?.activatedProductionNotebookComponents.push(
          notebookRoute.component === NotebookComponent
        );
        portHost.publish({
          noteId: notebookRoute.paramMap.get('noteId') ?? '',
          revisionId: notebookRoute.paramMap.get('revisionId')
        });
      }
    });
  }
}

const findActivatedNotebookRoute = (root: ActivatedRouteSnapshot): ActivatedRouteSnapshot | undefined => {
  let route: ActivatedRouteSnapshot | null = root;
  while (route) {
    if (route.component === NotebookComponent) {
      return route;
    }
    route = route.firstChild;
  }
  return undefined;
};

@Component({
  selector: 'zeppelin-notebook-core-port-proof',
  standalone: false,
  template: `
    <button type="button" data-testid="publish-notebook-core-revision" (click)="publishRevision()">
      publish revision
    </button>
    <div [zeppelin-react-mount]="'./NotebookCorePortProbe'" [reactProps]="reactProps"></div>
  `
})
export class NotebookCorePortProofComponent {
  readonly core: NotebookCorePort = Object.freeze({
    getSnapshot: () => this.snapshot,
    subscribe: listener => {
      this.listeners.add(listener);
      return () => this.listeners.delete(listener);
    }
  });

  readonly reactProps = {
    core: this.core,
    expectedCore: this.core,
    onProof: (proof: unknown) => {
      window.__zeppelinNotebookCorePortProof?.proofs.push(proof);
    },
    onReceivedCore: (receivedCore: NotebookCorePort) => {
      window.__zeppelinNotebookCorePortProof!.receivedCore = receivedCore;
    }
  };

  private snapshot: NotebookCoreSnapshot = { noteId: 'note-host-owned', revisionId: null };
  private readonly listeners = new Set<() => void>();

  constructor() {
    window.__zeppelinNotebookCorePortProof = {
      hostCore: this.core,
      proofs: []
    };
  }

  publishRevision(): void {
    this.snapshot = { noteId: 'note-host-owned', revisionId: 'revision-from-angular-host' };
    for (const listener of this.listeners) {
      listener();
    }
  }
}

@Injectable({ providedIn: 'root' })
export class NotebookRouteBoundaryPortHost {
  readonly core: NotebookCorePort = Object.freeze({
    getSnapshot: () => this.snapshot,
    subscribe: listener => {
      this.listeners.add(listener);
      return () => this.listeners.delete(listener);
    }
  });
  private snapshot: NotebookCoreSnapshot = { noteId: '', revisionId: null };
  private readonly listeners = new Set<() => void>();

  constructor() {
    window.__zeppelinNotebookRouteBoundaryProof = {
      activatedProductionNotebookComponents: [],
      connectionLifecycleCalls: [],
      hostCore: this.core,
      messageCalls: [],
      receivedCores: [],
      routePaths: NOTEBOOK_CHILD_ROUTE_PATHS.map(path => `${NOTEBOOK_ROUTE_PATH}/${path}`),
      workspaceGuardCalls: []
    };
  }

  publish(snapshot: NotebookCoreSnapshot): void {
    this.snapshot = snapshot;
    for (const listener of this.listeners) {
      listener();
    }
  }
}

class ProofMessageService {
  readonly connectedStatus = true;
  readonly connectedStatus$ = new BehaviorSubject(true);
  receive() {
    return NEVER;
  }
  receiveEnvelope() {
    return NEVER;
  }
  // The physical WebSocket lifecycle belongs to the shell. The proof records any call so that route activation
  // and port consumption can be shown not to reach it.
  bootstrap() {
    window.__zeppelinNotebookRouteBoundaryProof?.connectionLifecycleCalls.push('bootstrap');
  }
  close() {
    window.__zeppelinNotebookRouteBoundaryProof?.connectionLifecycleCalls.push('close');
  }
  connect() {
    window.__zeppelinNotebookRouteBoundaryProof?.connectionLifecycleCalls.push('connect');
  }
  getNote(noteId: string) {
    window.__zeppelinNotebookRouteBoundaryProof?.messageCalls.push({ method: 'getNote', noteId });
  }
  noteRevision(noteId: string, revisionId: string) {
    window.__zeppelinNotebookRouteBoundaryProof?.messageCalls.push({ method: 'noteRevision', noteId, revisionId });
  }
  listRevisionHistory(noteId: string) {
    window.__zeppelinNotebookRouteBoundaryProof?.messageCalls.push({ method: 'listRevisionHistory', noteId });
  }
}

@Injectable()
class ProofWorkspaceGuard {
  canActivate(_route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    window.__zeppelinNotebookRouteBoundaryProof?.workspaceGuardCalls.push(state.url);
    return true;
  }
}

@NgModule({
  bootstrap: [NotebookCorePortProofAppComponent],
  declarations: [NotebookCorePortProofAppComponent, NotebookCorePortProofComponent],
  imports: [
    BrowserModule,
    CommonModule,
    ShareModule,
    RouterModule.forRoot(
      [
        { path: 'port-identity', component: NotebookCorePortProofComponent },
        {
          path: '',
          loadChildren: () =>
            import('@zeppelin/pages/workspace/workspace.module').then(module => module.WorkspaceModule)
        }
      ],
      { useHash: true }
    )
  ],
  providers: [
    { provide: WorkspaceGuard, useClass: ProofWorkspaceGuard },
    { provide: MessageService, useClass: ProofMessageService },
    { provide: HeliumService, useValue: { initPackages: () => undefined } },
    { provide: NzMessageService, useValue: { loading: () => ({ messageId: 'proof' }), remove: () => undefined } },
    { provide: ReactFeatureService, useValue: { isEnabled: () => false } },
    {
      provide: ThemeService,
      useValue: { getCurrentTheme: () => 'light', theme$: NEVER, updateMonacoTheme: () => undefined }
    },
    {
      provide: TicketService,
      useValue: {
        getTicket: () => NEVER,
        ticket: { init: true, principal: 'anonymous', screenUsername: 'anonymous' }
      }
    },
    { provide: TRASH_FOLDER_ID_TOKEN, useValue: '~Trash' }
  ]
})
export class NotebookCorePortProofModule {}

void platformBrowserDynamic().bootstrapModule(NotebookCorePortProofModule);
