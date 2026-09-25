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

import { useEffect, useState } from 'react';
import { createRoot, Root } from 'react-dom/client';
import type { NotebookCorePort, NotebookCoreRemoteProps, NotebookCoreSnapshot } from '@zeppelin/notebook-core';

export type NotebookCorePortProbeProps = NotebookCoreRemoteProps &
  Readonly<{
    expectedCore?: NotebookCorePort;
    onReceivedCore?: (core: NotebookCorePort) => void;
    onProof?: (proof: NotebookCorePortProbeProof) => void;
  }>;

export type NotebookCorePortProbeProof = Readonly<{
  sameIdentity: boolean;
  snapshot: NotebookCoreSnapshot;
  updateCount: number;
}>;

export const NotebookCorePortProbe = ({ core, expectedCore, onProof, onReceivedCore }: NotebookCorePortProbeProps) => {
  const [snapshot, setSnapshot] = useState(() => core.getSnapshot());
  const [updateCount, setUpdateCount] = useState(0);
  const sameIdentity = Object.is(core, expectedCore);

  useEffect(() => {
    onProof?.({ sameIdentity, snapshot, updateCount });
    onReceivedCore?.(core);
  }, [core, onProof, onReceivedCore, sameIdentity, snapshot, updateCount]);

  useEffect(() => {
    return core.subscribe(() => {
      setSnapshot(core.getSnapshot());
      setUpdateCount(value => value + 1);
    });
  }, [core]);

  return (
    <section
      data-testid="notebook-core-port-probe"
      data-same-identity={sameIdentity ? 'true' : 'false'}
      data-note-id={snapshot.noteId}
      data-revision-id={snapshot.revisionId ?? ''}
      data-update-count={String(updateCount)}
    >
      <span>{snapshot.noteId}</span>
      <span>{snapshot.revisionId ?? 'live'}</span>
    </section>
  );
};

export interface NotebookCorePortProbeMountHandle {
  update: (props: NotebookCorePortProbeProps) => void;
  unmount: () => void;
}

export const mount = (
  element: HTMLElement,
  initialProps: NotebookCorePortProbeProps
): NotebookCorePortProbeMountHandle => {
  if (!element) {
    throw new Error('Mount element is required');
  }

  const root: Root = createRoot(element);

  const renderWith = (props: NotebookCorePortProbeProps) => {
    root.render(<NotebookCorePortProbe {...props} />);
  };

  renderWith(initialProps);

  return {
    update: (newProps: NotebookCorePortProbeProps) => {
      renderWith(newProps);
    },
    unmount: () => {
      root.unmount();
    }
  };
};
