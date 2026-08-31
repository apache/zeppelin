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
import { Component, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ReactMountDirective } from '@zeppelin/share/react-mount';
import type { NotebookCorePort, NotebookCoreSnapshot } from '@zeppelin/notebook-core';

declare global {
  interface Window {
    __zeppelinNotebookCorePortProof?: {
      hostCore: NotebookCorePort;
      proofs: unknown[];
      receivedCore?: NotebookCorePort;
    };
  }
}

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
      window.__zeppelinNotebookCorePortProof = window.__zeppelinNotebookCorePortProof ?? {
        hostCore: this.core,
        proofs: []
      };
      window.__zeppelinNotebookCorePortProof.receivedCore = receivedCore;
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

@NgModule({
  bootstrap: [NotebookCorePortProofComponent],
  declarations: [NotebookCorePortProofComponent, ReactMountDirective],
  imports: [BrowserModule, CommonModule]
})
export class NotebookCorePortProofModule {}

void platformBrowserDynamic().bootstrapModule(NotebookCorePortProofModule);
