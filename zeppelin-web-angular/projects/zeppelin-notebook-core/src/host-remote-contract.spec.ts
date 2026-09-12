// @vitest-environment node

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

import { assertType, describe, expect, it } from 'vitest';

import type {
  NotebookCorePort,
  NotebookCoreRemoteProps,
  NotebookCoreSnapshot,
  NotebookCoreSnapshotListener
} from './public-api';

// assertType does not invoke this callback; tsc checks the rejected mutations.
assertType<(props: NotebookCoreRemoteProps) => void>(props => {
  const snapshot = props.core.getSnapshot();

  // @ts-expect-error Snapshot note IDs are readonly.
  snapshot.noteId = 'another-note';
  // @ts-expect-error Snapshot revision IDs are readonly.
  snapshot.revisionId = 'another-revision';
  // @ts-expect-error The remote cannot replace the shared core port.
  props.core = { ...props.core };
  // @ts-expect-error The core snapshot reader is readonly.
  props.core.getSnapshot = () => snapshot;
  // @ts-expect-error The core subscription method is readonly.
  props.core.subscribe = () => () => undefined;
});

const fakeCorePort = (initialSnapshot: NotebookCoreSnapshot) => {
  let snapshot = initialSnapshot;
  const listeners = new Set<NotebookCoreSnapshotListener>();
  const core: NotebookCorePort = {
    getSnapshot: () => snapshot,
    subscribe: listener => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }
  };

  return {
    core,
    publish: (nextSnapshot: NotebookCoreSnapshot) => {
      snapshot = nextSnapshot;
      for (const listener of listeners) {
        listener();
      }
    }
  };
};

describe('notebook core host and remote contract', () => {
  it('demonstrates snapshot subscription and cleanup with a fake host-owned port', () => {
    const host = fakeCorePort({ noteId: '2A94M5J1Z', revisionId: null });
    const remoteProps: NotebookCoreRemoteProps = { core: host.core };
    const snapshots: unknown[] = [];

    expect(remoteProps.core).toBe(host.core);

    const unsubscribe = remoteProps.core.subscribe(() => snapshots.push(remoteProps.core.getSnapshot()));
    host.publish({ noteId: '2A94M5J1Z', revisionId: 'rev-1' });
    unsubscribe();
    host.publish({ noteId: '2A94M5J1Z', revisionId: 'rev-2' });

    expect(snapshots).toEqual([{ noteId: '2A94M5J1Z', revisionId: 'rev-1' }]);
    expect(remoteProps.core.getSnapshot()).toEqual({ noteId: '2A94M5J1Z', revisionId: 'rev-2' });
  });
});
