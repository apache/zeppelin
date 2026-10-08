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

import type {
  NotebookAclInput,
  NotebookAclStatus,
  NotebookNoteInput,
  NotebookParagraphInput,
  NotebookReadAcl,
  NotebookReadFailure,
  NotebookReadNote,
  NotebookReadParagraph,
  NotebookReadSnapshot,
  NotebookReadStatus,
  NotebookReadTarget,
  NotebookRevisionInput
} from './read-model';

/**
 * The lifetime of one permissions read. Responses are accepted only while this is the latest
 * permissions read of the current activation.
 */
export interface NotebookAclRequest {
  isCurrent(): boolean;
  accept(acl: NotebookAclInput): boolean;
  fail(reason: 'access-denied' | 'failed'): boolean;
}

/**
 * The lifetime of one activation. The host keeps this handle with the request it sends and passes
 * the response through it. Once another activation, a deactivation or dispose replaces it, every
 * method rejects, including for the same target visited again.
 *
 * Each method returns whether the input was applied.
 */
export interface NotebookReadRequest {
  readonly target: NotebookReadTarget;
  isCurrent(): boolean;
  /** A full NOTE for a live-note target. Accepted while loading or ready. */
  acceptNote(note: NotebookNoteInput): boolean;
  /** A full NOTE_REVISION for a revision target. A response without a note is `not-found`. */
  acceptRevision(revision: NotebookRevisionInput): boolean;
  /** Ends a load that has not completed. A failure ends this request; activate again to retry. */
  fail(reason: NotebookReadFailure): boolean;
  /** Starts a permissions read. A later call replaces an earlier one. */
  readAcl(): NotebookAclRequest;
}

export interface NotebookReadStore {
  /** Returns the same reference until the read state changes. */
  getSnapshot(): NotebookReadSnapshot;
  /** Listeners run in subscription order, once per snapshot change. */
  subscribe(listener: () => void): () => void;
  activate(target: NotebookReadTarget): NotebookReadRequest;
  deactivate(): void;
  /** Publishes a final `disposed` snapshot, then drops every listener. */
  dispose(): void;
}

// Read state is built from wire data: null, booleans, numbers, strings, arrays and plain objects.
const freezeCopy = (value: unknown): unknown => {
  if (Array.isArray(value)) {
    return Object.freeze(value.map(freezeCopy));
  }
  if (value !== null && typeof value === 'object') {
    const copy: { [key: string]: unknown } = {};
    for (const key of Object.keys(value)) {
      copy[key] = freezeCopy((value as { [key: string]: unknown })[key]);
    }
    return Object.freeze(copy);
  }
  return value;
};

const sameValue = (a: unknown, b: unknown): boolean => {
  if (a === b) {
    return true;
  }
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, index) => sameValue(item, b[index]));
  }
  if (a !== null && b !== null && typeof a === 'object' && typeof b === 'object') {
    if (Array.isArray(a) || Array.isArray(b)) {
      return false;
    }
    const aKeys = Object.keys(a);
    const bKeys = Object.keys(b);
    return (
      aKeys.length === bKeys.length &&
      aKeys.every(
        key =>
          Object.prototype.hasOwnProperty.call(b, key) &&
          sameValue((a as { [key: string]: unknown })[key], (b as { [key: string]: unknown })[key])
      )
    );
  }
  return false;
};

/** Keeps the previous object when the next one has the same content. */
const reuse = <T>(previous: T | undefined, next: T): T =>
  previous !== undefined && sameValue(previous, next) ? previous : next;

const emptyAcl = (status: NotebookAclStatus): NotebookReadAcl =>
  freezeCopy({ status, owners: [], readers: [], runners: [], writers: [] }) as NotebookReadAcl;

const unknownAcl = emptyAcl('unknown');
const loadingAcl = emptyAcl('loading');

const buildParagraph = (input: NotebookParagraphInput): NotebookReadParagraph => {
  const chartConfigs: { [resultIndex: string]: unknown } = {};
  const configResults = input.config?.results ?? {};
  for (const resultIndex of Object.keys(configResults)) {
    const graph = configResults[resultIndex]?.graph;
    if (graph !== undefined) {
      chartConfigs[resultIndex] = graph;
    }
  }
  return freezeCopy({
    id: input.id,
    title: input.title ?? null,
    text: input.text ?? '',
    status: input.status,
    resultCode: input.results?.code ?? null,
    resultMessages: (input.results?.msg ?? []).map(message => ({ type: message.type, data: message.data })),
    chartConfigs
  }) as NotebookReadParagraph;
};

const buildNote = (input: NotebookNoteInput, previous: NotebookReadNote | null): NotebookReadNote => {
  const paragraphsById: { [paragraphId: string]: NotebookReadParagraph } = {};
  for (const paragraphInput of input.paragraphs) {
    const previousParagraph =
      previous && Object.prototype.hasOwnProperty.call(previous.paragraphsById, paragraphInput.id)
        ? previous.paragraphsById[paragraphInput.id]
        : undefined;
    paragraphsById[paragraphInput.id] = reuse(previousParagraph, buildParagraph(paragraphInput));
  }
  const next: NotebookReadNote = Object.freeze({
    id: input.id,
    name: input.name,
    path: input.path ?? null,
    paragraphIds: reuse(previous?.paragraphIds, Object.freeze(input.paragraphs.map(paragraph => paragraph.id))),
    paragraphsById: Object.freeze(paragraphsById)
  });
  return reuse(previous ?? undefined, next);
};

const makeSnapshot = (
  target: NotebookReadTarget | null,
  status: NotebookReadStatus,
  note: NotebookReadNote | null,
  acl: NotebookReadAcl
): NotebookReadSnapshot =>
  Object.freeze({
    noteId: target?.noteId ?? '',
    revisionId: target?.kind === 'revision' ? target.revisionId : null,
    target,
    status,
    note,
    acl
  });

const idleSnapshot = makeSnapshot(null, 'idle', null, unknownAcl);
const disposedSnapshot = makeSnapshot(null, 'disposed', null, unknownAcl);

const staleAclRequest: NotebookAclRequest = Object.freeze({
  isCurrent: () => false,
  accept: () => false,
  fail: () => false
});

export const createNotebookReadStore = (): NotebookReadStore => {
  let snapshot = idleSnapshot;
  // One entry per subscription, so subscribing the same function twice needs two unsubscribes.
  let subscriptions: Array<{ listener: () => void }> = [];
  // Identity tokens for the current activation and permissions read. A handle compares its own
  // token with these, so a replaced handle stays rejected even when the same target returns.
  let activeRequest: object | null = null;
  let activeAclRequest: object | null = null;
  let disposed = false;

  const publish = (next: NotebookReadSnapshot): void => {
    if (next === snapshot) {
      return;
    }
    snapshot = next;
    // subscribe and unsubscribe replace the array, so changes made while notifying take effect
    // from the next change.
    for (const subscription of subscriptions) {
      subscription.listener();
    }
  };

  // Changes the read state of the current target; activation publishes a new target directly.
  const update = (changes: Partial<Pick<NotebookReadSnapshot, 'status' | 'note' | 'acl'>>): void => {
    const status = changes.status ?? snapshot.status;
    const note = 'note' in changes ? (changes.note ?? null) : snapshot.note;
    const acl = changes.acl ?? snapshot.acl;
    if (status === snapshot.status && note === snapshot.note && acl === snapshot.acl) {
      return;
    }
    publish(makeSnapshot(snapshot.target, status, note, acl));
  };

  const activate = (requestedTarget: NotebookReadTarget): NotebookReadRequest => {
    if (disposed) {
      throw new Error('Cannot activate a disposed notebook read store');
    }
    const token = {};
    activeRequest = token;
    activeAclRequest = null;
    const target = freezeCopy(requestedTarget) as NotebookReadTarget;
    publish(makeSnapshot(target, 'loading', null, unknownAcl));

    const isCurrent = (): boolean => activeRequest === token;
    const canLoad = (): boolean => isCurrent() && (snapshot.status === 'loading' || snapshot.status === 'ready');

    // A permissions re-read passes through `loading`; reuse the last lists read in this activation.
    let lastReadyAcl: NotebookReadAcl | undefined;

    const acceptFullNote = (input: NotebookNoteInput): void => {
      const previous = snapshot.status === 'ready' ? snapshot.note : null;
      update({ status: 'ready', note: buildNote(input, previous) });
    };

    const readAcl = (): NotebookAclRequest => {
      if (!isCurrent()) {
        return staleAclRequest;
      }
      const aclToken = {};
      activeAclRequest = aclToken;
      update({ acl: loadingAcl });
      const isAclCurrent = (): boolean => isCurrent() && activeAclRequest === aclToken;
      return Object.freeze({
        isCurrent: isAclCurrent,
        accept: (input: NotebookAclInput): boolean => {
          if (!isAclCurrent()) {
            return false;
          }
          const acl = freezeCopy({
            status: 'ready',
            owners: input.owners,
            readers: input.readers,
            runners: input.runners,
            writers: input.writers
          }) as NotebookReadAcl;
          lastReadyAcl = reuse(lastReadyAcl, acl);
          update({ acl: lastReadyAcl });
          return true;
        },
        fail: (reason: 'access-denied' | 'failed'): boolean => {
          if (!isAclCurrent()) {
            return false;
          }
          update({ acl: emptyAcl(reason) });
          return true;
        }
      });
    };

    return Object.freeze({
      target,
      isCurrent,
      acceptNote: (input: NotebookNoteInput): boolean => {
        if (!canLoad() || target.kind !== 'note' || input.id !== target.noteId) {
          return false;
        }
        acceptFullNote(input);
        return true;
      },
      acceptRevision: (input: NotebookRevisionInput): boolean => {
        if (
          !canLoad() ||
          target.kind !== 'revision' ||
          input.noteId !== target.noteId ||
          input.revisionId !== target.revisionId
        ) {
          return false;
        }
        if (input.note === undefined || input.note === null) {
          if (snapshot.status !== 'loading') {
            return false;
          }
          update({ status: 'not-found', note: null });
          return true;
        }
        if (input.note.id !== target.noteId) {
          return false;
        }
        acceptFullNote(input.note);
        return true;
      },
      fail: (reason: NotebookReadFailure): boolean => {
        if (!isCurrent() || snapshot.status !== 'loading') {
          return false;
        }
        update({ status: reason, note: null });
        return true;
      },
      readAcl
    });
  };

  return Object.freeze({
    getSnapshot: () => snapshot,
    subscribe: (listener: () => void) => {
      if (disposed) {
        return () => undefined;
      }
      const subscription = { listener };
      subscriptions = [...subscriptions, subscription];
      return () => {
        subscriptions = subscriptions.filter(registered => registered !== subscription);
      };
    },
    activate,
    deactivate: () => {
      if (disposed) {
        return;
      }
      activeRequest = null;
      activeAclRequest = null;
      publish(idleSnapshot);
    },
    dispose: () => {
      if (disposed) {
        return;
      }
      disposed = true;
      activeRequest = null;
      activeAclRequest = null;
      publish(disposedSnapshot);
      subscriptions = [];
    }
  });
};
