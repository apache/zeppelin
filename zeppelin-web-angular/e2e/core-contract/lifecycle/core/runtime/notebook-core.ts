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
  NotebookCoreCommandHandler,
  NotebookCorePort,
  NotebookCoreSnapshot,
  NotebookCoreSnapshotListener,
  NotebookDynamicForms,
  NotebookFormParams,
  NotebookLookAndFeel,
  NotebookParagraphInput,
  NotebookParagraphResult,
  NotebookParagraphResultConfig,
  NotebookParagraphResultConfigs,
  NotebookParagraphSnapshot,
  NotebookPermissions,
  NotebookRevision,
  NotebookSchedule
} from './host-remote-contract';

export type NotebookCoreEvent =
  | Readonly<{ type: 'route-changed'; noteId: string; revisionId: string | null }>
  | Readonly<{ type: 'load-started' }>
  | Readonly<{
      type: 'note-loaded';
      noteId: string;
      revisionId: string | null;
      title: string;
      noteForms?: NotebookDynamicForms;
      noteParams?: NotebookFormParams;
      scheduler?: NotebookSchedule;
      lookAndFeel?: NotebookLookAndFeel;
      personalizedMode?: boolean;
      paragraphs: readonly NotebookParagraphInput[];
    }>
  | Readonly<{ type: 'paragraph-added'; index: number; paragraph: NotebookParagraphInput }>
  | Readonly<{ type: 'paragraph-removed'; paragraphId: string }>
  | Readonly<{ type: 'paragraph-moved'; paragraphId: string; index: number }>
  | Readonly<{
      type: 'paragraph-updated';
      paragraphId: string;
      text?: string;
      status?: NotebookParagraphSnapshot['status'];
      language?: string;
      resultConfigs?: NotebookParagraphResultConfigs;
      source?: 'local' | 'server' | 'collaboration';
    }>
  | Readonly<{ type: 'paragraph-progressed'; paragraphId: string; progress: number }>
  | Readonly<{
      type: 'paragraph-output-updated';
      paragraphId: string;
      index: number;
      result: NotebookParagraphResult;
      outputSequence?: number;
    }>
  | Readonly<{
      type: 'paragraph-output-appended';
      paragraphId: string;
      index: number;
      data: string;
      outputSequence?: number;
    }>
  | Readonly<{
      type: 'paragraph-output-snapshotted';
      paragraphId: string;
      results: readonly NotebookParagraphResult[];
      outputSequence: number;
    }>
  | Readonly<{ type: 'paragraph-save-requested'; paragraphId: string }>
  | Readonly<{ type: 'paragraph-save-cancelled'; paragraphId: string }>
  | Readonly<{ type: 'paragraph-run-requested'; paragraphId: string }>
  | Readonly<{ type: 'paragraph-run-rejected'; paragraphId: string }>
  | Readonly<{ type: 'note-updated'; title: string }>
  | Readonly<{ type: 'note-forms-updated'; noteForms: NotebookDynamicForms; noteParams: NotebookFormParams }>
  | Readonly<{ type: 'permissions-updated'; permissions: NotebookPermissions }>
  | Readonly<{ type: 'collaboration-updated'; users: readonly string[] | null }>
  | Readonly<{ type: 'schedule-updated'; scheduler: NotebookSchedule | null }>
  | Readonly<{ type: 'look-and-feel-updated'; lookAndFeel: NotebookLookAndFeel }>
  | Readonly<{ type: 'personalized-mode-updated'; personalizedMode: boolean }>
  | Readonly<{ type: 'revisions-updated'; revisions: readonly NotebookRevision[] }>
  | Readonly<{ type: 'load-failed'; noteId: string; revisionId: string | null; error: string }>;

export type NotebookCoreRuntime = Readonly<{
  port: NotebookCorePort;
  apply: (event: NotebookCoreEvent) => boolean;
}>;

export type NotebookCoreInitialRoute = Readonly<{
  noteId?: string;
  revisionId?: string | null;
  dispatchCommand?: NotebookCoreCommandHandler;
}>;

export const selectNotebookParagraphViews = <T extends Readonly<{ id: string }>>(
  snapshot: NotebookCoreSnapshot,
  paragraphViewsById: ReadonlyMap<string, T>
): readonly T[] =>
  snapshot.paragraphs.map(paragraph => {
    const paragraphView = paragraphViewsById.get(paragraph.id);
    if (!paragraphView) {
      throw new Error(`Missing paragraph view for Core paragraph ${paragraph.id}`);
    }
    return paragraphView;
  });

type NotebookCoreState = Readonly<{
  version: number;
  noteId: string;
  revisionId: string | null;
  phase: NotebookCoreSnapshot['phase'];
  title: string | null;
  noteForms: NotebookDynamicForms;
  noteParams: NotebookFormParams;
  permissions: NotebookPermissions | null;
  collaborativeUsers: readonly string[] | null;
  scheduler: NotebookSchedule | null;
  lookAndFeel: NotebookLookAndFeel;
  personalizedMode: boolean;
  revisions: readonly NotebookRevision[];
  paragraphOrder: readonly string[];
  paragraphsById: Readonly<Record<string, NotebookParagraphState>>;
  error: string | null;
}>;

type NotebookParagraphState = Readonly<{
  snapshot: NotebookParagraphSnapshot;
  savedText: string;
  savePending: boolean;
  pendingRunStatus: NotebookParagraphSnapshot['status'] | null;
  outputSequence: number | null;
}>;

const freezeParagraph = (paragraph: NotebookParagraphState): NotebookParagraphState => Object.freeze({ ...paragraph });

const freezeFormValue = (value: NotebookFormParams[string]): NotebookFormParams[string] =>
  Array.isArray(value) ? Object.freeze([...value]) : value;

const freezeNoteForms = (forms: NotebookDynamicForms = {}): NotebookDynamicForms =>
  Object.freeze(
    Object.entries(forms).reduce<Record<string, (typeof forms)[string]>>((result, [name, form]) => {
      result[name] = Object.freeze({
        ...form,
        defaultValue: freezeFormValue(form.defaultValue),
        ...(form.options ? { options: Object.freeze(form.options.map(option => Object.freeze({ ...option }))) } : {})
      });
      return result;
    }, {})
  );

const freezeNoteParams = (params: NotebookFormParams = {}): NotebookFormParams =>
  Object.freeze(
    Object.entries(params).reduce<Record<string, NotebookFormParams[string]>>((result, [name, value]) => {
      result[name] = freezeFormValue(value);
      return result;
    }, {})
  );

const freezePermissions = (permissions: NotebookPermissions): NotebookPermissions =>
  Object.freeze({
    readers: Object.freeze([...permissions.readers]),
    owners: Object.freeze([...permissions.owners]),
    writers: Object.freeze([...permissions.writers]),
    runners: Object.freeze([...permissions.runners])
  });

const freezeSchedule = (scheduler: NotebookSchedule): NotebookSchedule =>
  Object.freeze({
    ...(scheduler.cron ? { cron: scheduler.cron } : {}),
    releaseResource: scheduler.releaseResource
  });

const freezeRevisions = (revisions: readonly NotebookRevision[]): readonly NotebookRevision[] =>
  Object.freeze(revisions.map(revision => Object.freeze({ ...revision })));

const freezeResultConfigValue = (value: unknown): unknown => {
  if (Array.isArray(value)) {
    return Object.freeze(value.map(freezeResultConfigValue));
  }
  if (value && typeof value === 'object') {
    return Object.freeze(
      Object.entries(value).reduce<Record<string, unknown>>((result, [key, nestedValue]) => {
        result[key] = freezeResultConfigValue(nestedValue);
        return result;
      }, {})
    );
  }
  return value;
};

const freezeResultConfigs = (configs: NotebookParagraphResultConfigs): NotebookParagraphResultConfigs =>
  Object.freeze(
    Object.entries(configs).reduce<Record<string, NotebookParagraphResultConfig>>((result, [index, config]) => {
      result[index] = Object.freeze({
        graph: freezeResultConfigValue(config.graph) as NotebookParagraphResultConfig['graph']
      });
      return result;
    }, {})
  );

const freezeParagraphSnapshot = (
  paragraph: NotebookParagraphInput,
  savedText: string = paragraph.text
): NotebookParagraphSnapshot =>
  Object.freeze({
    id: paragraph.id,
    text: paragraph.text,
    status: paragraph.status,
    ...(paragraph.language ? { language: paragraph.language } : {}),
    progress: paragraph.progress ?? 0,
    isDirty: paragraph.text !== savedText,
    ...(paragraph.results
      ? { results: Object.freeze(paragraph.results.map(result => Object.freeze({ ...result }))) }
      : {}),
    ...(paragraph.resultConfigs ? { resultConfigs: freezeResultConfigs(paragraph.resultConfigs) } : {})
  });

const freezeState = (state: NotebookCoreState): NotebookCoreState =>
  Object.freeze({
    ...state,
    paragraphOrder: Object.freeze([...state.paragraphOrder]),
    paragraphsById: Object.freeze({ ...state.paragraphsById })
  });

const toSnapshot = (state: NotebookCoreState): NotebookCoreSnapshot =>
  Object.freeze({
    version: state.version,
    noteId: state.noteId,
    revisionId: state.revisionId,
    phase: state.phase,
    title: state.title,
    noteForms: state.noteForms,
    noteParams: state.noteParams,
    ...(state.permissions ? { permissions: state.permissions } : {}),
    ...(state.collaborativeUsers ? { collaborativeUsers: state.collaborativeUsers } : {}),
    ...(state.scheduler ? { scheduler: state.scheduler } : {}),
    ...(state.revisions.length > 0 ? { revisions: state.revisions } : {}),
    lookAndFeel: state.lookAndFeel,
    personalizedMode: state.personalizedMode,
    paragraphs: Object.freeze(state.paragraphOrder.map(paragraphId => state.paragraphsById[paragraphId].snapshot)),
    error: state.error
  });

const emptyParagraphState = (): Pick<NotebookCoreState, 'paragraphOrder' | 'paragraphsById'> => ({
  paragraphOrder: [],
  paragraphsById: {}
});

const toParagraphState = (
  paragraphs: readonly NotebookParagraphInput[]
): Pick<NotebookCoreState, 'paragraphOrder' | 'paragraphsById'> => {
  const paragraphsById: Record<string, NotebookParagraphState> = {};
  const paragraphOrder: string[] = [];
  for (const paragraph of paragraphs) {
    if (paragraphsById[paragraph.id]) {
      continue;
    }
    paragraphsById[paragraph.id] = freezeParagraph({
      snapshot: freezeParagraphSnapshot(paragraph),
      savedText: paragraph.text,
      savePending: false,
      pendingRunStatus: null,
      outputSequence: null
    });
    paragraphOrder.push(paragraph.id);
  }
  return { paragraphOrder, paragraphsById };
};

const initialState = (route: NotebookCoreInitialRoute): NotebookCoreState =>
  freezeState({
    version: 0,
    noteId: route.noteId ?? '',
    revisionId: route.revisionId ?? null,
    phase: 'idle',
    title: null,
    noteForms: freezeNoteForms(),
    noteParams: freezeNoteParams(),
    permissions: null,
    collaborativeUsers: null,
    scheduler: null,
    lookAndFeel: 'default',
    personalizedMode: false,
    revisions: Object.freeze([]),
    ...emptyParagraphState(),
    error: null
  });

const clampIndex = (index: number, length: number): number => Math.min(Math.max(index, 0), length);

const hasSameParagraphOrder = (left: readonly string[], right: readonly string[]): boolean =>
  left.length === right.length && left.every((paragraphId, index) => paragraphId === right[index]);

const isLiveMutation = (event: NotebookCoreEvent): boolean =>
  event.type === 'paragraph-added' ||
  event.type === 'paragraph-removed' ||
  event.type === 'paragraph-moved' ||
  event.type === 'paragraph-updated' ||
  event.type === 'paragraph-output-updated' ||
  event.type === 'paragraph-output-appended' ||
  event.type === 'note-updated';

const reduceState = (state: NotebookCoreState, event: NotebookCoreEvent): NotebookCoreState => {
  const version = state.version + 1;
  if (state.revisionId !== null && isLiveMutation(event)) {
    return state;
  }
  switch (event.type) {
    case 'route-changed':
      return freezeState({
        version,
        noteId: event.noteId,
        revisionId: event.revisionId,
        phase: 'idle',
        title: null,
        noteForms: freezeNoteForms(),
        noteParams: freezeNoteParams(),
        permissions: null,
        collaborativeUsers: null,
        scheduler: null,
        lookAndFeel: 'default',
        personalizedMode: false,
        revisions: Object.freeze([]),
        ...emptyParagraphState(),
        error: null
      });
    case 'load-started':
      return freezeState({
        ...state,
        version,
        phase: 'loading',
        title: null,
        noteForms: freezeNoteForms(),
        noteParams: freezeNoteParams(),
        permissions: null,
        collaborativeUsers: null,
        scheduler: null,
        lookAndFeel: 'default',
        personalizedMode: false,
        revisions: Object.freeze([]),
        ...emptyParagraphState(),
        error: null
      });
    case 'note-loaded':
      if (event.noteId !== state.noteId || event.revisionId !== state.revisionId) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        phase: 'ready',
        title: event.title,
        noteForms: freezeNoteForms(event.noteForms),
        noteParams: freezeNoteParams(event.noteParams),
        scheduler: event.scheduler ? freezeSchedule(event.scheduler) : null,
        lookAndFeel: event.lookAndFeel ?? 'default',
        personalizedMode: event.personalizedMode ?? false,
        ...toParagraphState(event.paragraphs),
        error: null
      });
    case 'paragraph-added': {
      if (state.phase !== 'ready' || state.paragraphsById[event.paragraph.id]) {
        return state;
      }
      const index = clampIndex(event.index, state.paragraphOrder.length);
      const paragraphOrder = [
        ...state.paragraphOrder.slice(0, index),
        event.paragraph.id,
        ...state.paragraphOrder.slice(index)
      ];
      return freezeState({
        ...state,
        version,
        paragraphOrder,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraph.id]: freezeParagraph({
            snapshot: freezeParagraphSnapshot(event.paragraph),
            savedText: event.paragraph.text,
            savePending: false,
            pendingRunStatus: null,
            outputSequence: null
          })
        }
      });
    }
    case 'paragraph-removed': {
      if (state.phase !== 'ready' || !state.paragraphsById[event.paragraphId]) {
        return state;
      }
      const { [event.paragraphId]: _, ...paragraphsById } = state.paragraphsById;
      return freezeState({
        ...state,
        version,
        paragraphOrder: state.paragraphOrder.filter(paragraphId => paragraphId !== event.paragraphId),
        paragraphsById
      });
    }
    case 'paragraph-moved': {
      if (state.phase !== 'ready' || !state.paragraphsById[event.paragraphId]) {
        return state;
      }
      const remaining = state.paragraphOrder.filter(paragraphId => paragraphId !== event.paragraphId);
      const index = clampIndex(event.index, remaining.length);
      const paragraphOrder = [...remaining.slice(0, index), event.paragraphId, ...remaining.slice(index)];
      if (hasSameParagraphOrder(state.paragraphOrder, paragraphOrder)) {
        return state;
      }
      return freezeState({ ...state, version, paragraphOrder });
    }
    case 'paragraph-updated': {
      if (state.phase !== 'ready') {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (!current) {
        return state;
      }
      const { snapshot: currentSnapshot } = current;
      const isCollaborationTextUpdate = event.source === 'collaboration' && event.text !== undefined;
      const isServerTextUpdate =
        (event.source === 'server' || event.source === 'collaboration') && event.text !== undefined;
      const hadLocalDraft = currentSnapshot.text !== current.savedText;
      const serverText =
        isCollaborationTextUpdate && hadLocalDraft
          ? current.savedText
          : isServerTextUpdate
            ? event.text
            : current.savedText;
      const paragraph = {
        ...current,
        snapshot: freezeParagraphSnapshot(
          {
            id: currentSnapshot.id,
            text: isCollaborationTextUpdate
              ? event.text
              : isServerTextUpdate && currentSnapshot.text !== current.savedText
                ? currentSnapshot.text
                : (event.text ?? currentSnapshot.text),
            status: event.status ?? currentSnapshot.status,
            language: event.language ?? currentSnapshot.language,
            progress: currentSnapshot.progress,
            results: currentSnapshot.results,
            resultConfigs: event.resultConfigs ?? currentSnapshot.resultConfigs
          },
          serverText
        ),
        savedText: serverText,
        savePending: isCollaborationTextUpdate ? current.savePending : isServerTextUpdate ? false : current.savePending,
        pendingRunStatus: event.status !== undefined && event.status !== 'PENDING' ? null : current.pendingRunStatus,
        outputSequence: event.status === 'PENDING' ? null : current.outputSequence
      };
      if (
        paragraph.snapshot.text === currentSnapshot.text &&
        paragraph.savedText === current.savedText &&
        paragraph.snapshot.status === currentSnapshot.status &&
        paragraph.savePending === current.savePending &&
        paragraph.pendingRunStatus === current.pendingRunStatus &&
        event.language === undefined &&
        event.resultConfigs === undefined
      ) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: { ...state.paragraphsById, [event.paragraphId]: freezeParagraph(paragraph) }
      });
    }
    case 'paragraph-progressed': {
      if (state.phase !== 'ready' || !Number.isFinite(event.progress)) {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (!current) {
        return state;
      }
      const progress = Math.max(0, Math.min(100, event.progress));
      if (current.snapshot.progress === progress) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({
            ...current,
            snapshot: freezeParagraphSnapshot({ ...current.snapshot, progress }, current.savedText)
          })
        }
      });
    }
    case 'paragraph-output-updated': {
      if (state.phase !== 'ready' || event.index < 0) {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (!current) {
        return state;
      }
      if (
        event.outputSequence !== undefined &&
        (!Number.isSafeInteger(event.outputSequence) ||
          (current.outputSequence !== null && event.outputSequence <= current.outputSequence))
      ) {
        return state;
      }
      const results = [...(current.snapshot.results ?? [])];
      results[event.index] = Object.freeze({ ...event.result });
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({
            ...current,
            outputSequence: event.outputSequence ?? current.outputSequence,
            snapshot: freezeParagraphSnapshot({ ...current.snapshot, results }, current.savedText)
          })
        }
      });
    }
    case 'paragraph-output-appended': {
      if (state.phase !== 'ready' || event.index < 0) {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (!current) {
        return state;
      }
      if (
        event.outputSequence !== undefined &&
        (!Number.isSafeInteger(event.outputSequence) ||
          (current.outputSequence !== null && event.outputSequence <= current.outputSequence))
      ) {
        return state;
      }
      const results = [...(current.snapshot.results ?? [])];
      const existing = results[event.index] ?? { type: 'TEXT', data: '' };
      results[event.index] = Object.freeze({ ...existing, data: existing.data + event.data });
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({
            ...current,
            outputSequence: event.outputSequence ?? current.outputSequence,
            snapshot: freezeParagraphSnapshot({ ...current.snapshot, results }, current.savedText)
          })
        }
      });
    }
    case 'paragraph-output-snapshotted': {
      if (state.phase !== 'ready' || !Number.isSafeInteger(event.outputSequence) || event.outputSequence < 0) {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (!current) {
        return state;
      }
      if (current.outputSequence !== null && event.outputSequence < current.outputSequence) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({
            ...current,
            outputSequence: event.outputSequence,
            snapshot: freezeParagraphSnapshot(
              {
                ...current.snapshot,
                results: event.results.map(result => Object.freeze({ ...result }))
              },
              current.savedText
            )
          })
        }
      });
    }
    case 'paragraph-save-requested': {
      if (state.phase !== 'ready') {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (!current || current.savePending || current.snapshot.text === current.savedText) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({ ...current, savePending: true })
        }
      });
    }
    case 'paragraph-save-cancelled': {
      const current = state.paragraphsById[event.paragraphId];
      if (!current?.savePending) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({ ...current, savePending: false })
        }
      });
    }
    case 'paragraph-run-requested': {
      if (state.phase !== 'ready') {
        return state;
      }
      const current = state.paragraphsById[event.paragraphId];
      if (
        !current ||
        current.snapshot.text.length === 0 ||
        current.snapshot.status === 'PENDING' ||
        current.snapshot.status === 'RUNNING'
      ) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({
            ...current,
            snapshot: freezeParagraphSnapshot({ ...current.snapshot, status: 'PENDING' }, current.savedText),
            pendingRunStatus: current.snapshot.status
          })
        }
      });
    }
    case 'paragraph-run-rejected': {
      const current = state.paragraphsById[event.paragraphId];
      if (!current?.pendingRunStatus) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        paragraphsById: {
          ...state.paragraphsById,
          [event.paragraphId]: freezeParagraph({
            ...current,
            snapshot: freezeParagraphSnapshot(
              { ...current.snapshot, status: current.pendingRunStatus },
              current.savedText
            ),
            pendingRunStatus: null
          })
        }
      });
    }
    case 'note-updated':
      if (state.phase !== 'ready' || state.title === event.title) {
        return state;
      }
      return freezeState({ ...state, version, title: event.title });
    case 'note-forms-updated':
      if (state.phase !== 'ready') {
        return state;
      }
      return freezeState({
        ...state,
        version,
        noteForms: freezeNoteForms(event.noteForms),
        noteParams: freezeNoteParams(event.noteParams)
      });
    case 'permissions-updated':
      if (state.phase !== 'ready') {
        return state;
      }
      return freezeState({ ...state, version, permissions: freezePermissions(event.permissions) });
    case 'collaboration-updated':
      if (state.phase !== 'ready') {
        return state;
      }
      return freezeState({
        ...state,
        version,
        collaborativeUsers: event.users ? Object.freeze([...event.users]) : null
      });
    case 'schedule-updated':
      if (state.phase !== 'ready') {
        return state;
      }
      return freezeState({ ...state, version, scheduler: event.scheduler ? freezeSchedule(event.scheduler) : null });
    case 'look-and-feel-updated':
      if (state.phase !== 'ready' || state.lookAndFeel === event.lookAndFeel) {
        return state;
      }
      return freezeState({ ...state, version, lookAndFeel: event.lookAndFeel });
    case 'personalized-mode-updated':
      if (state.phase !== 'ready' || state.personalizedMode === event.personalizedMode) {
        return state;
      }
      return freezeState({ ...state, version, personalizedMode: event.personalizedMode });
    case 'revisions-updated':
      if (state.phase !== 'ready') {
        return state;
      }
      return freezeState({ ...state, version, revisions: freezeRevisions(event.revisions) });
    case 'load-failed':
      if (event.noteId !== state.noteId || event.revisionId !== state.revisionId) {
        return state;
      }
      return freezeState({
        ...state,
        version,
        phase: 'error',
        title: null,
        noteForms: freezeNoteForms(),
        noteParams: freezeNoteParams(),
        permissions: null,
        collaborativeUsers: null,
        scheduler: null,
        lookAndFeel: 'default',
        personalizedMode: false,
        revisions: Object.freeze([]),
        ...emptyParagraphState(),
        error: event.error
      });
  }
};

export const createNotebookCore = (route: NotebookCoreInitialRoute = {}): NotebookCoreRuntime => {
  let state = initialState(route);
  let snapshot = toSnapshot(state);
  const listeners = new Set<NotebookCoreSnapshotListener>();
  const queuedSaveRequests = new Set<string>();
  let dispatchQueuedSave: NotebookCoreCommandHandler = () => false;
  const apply = (event: NotebookCoreEvent): boolean => {
    const nextState = reduceState(state, event);
    if (nextState === state) {
      return false;
    }
    if (event.type === 'route-changed' || event.type === 'load-started' || event.type === 'note-loaded') {
      queuedSaveRequests.clear();
    } else if (event.type === 'paragraph-removed') {
      queuedSaveRequests.delete(event.paragraphId);
    }
    state = nextState;
    snapshot = toSnapshot(state);
    listeners.forEach(listener => listener());
    if (
      event.type === 'paragraph-updated' &&
      event.source === 'server' &&
      queuedSaveRequests.delete(event.paragraphId)
    ) {
      const paragraph = state.paragraphsById[event.paragraphId];
      if (paragraph && !paragraph.savePending && paragraph.snapshot.text !== paragraph.savedText) {
        dispatchQueuedSave({ type: 'commit-paragraph', paragraphId: event.paragraphId });
      }
    }
    return true;
  };
  const port: NotebookCorePort = Object.freeze({
    getSnapshot: () => snapshot,
    subscribe: listener => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    dispatch: command => {
      if (
        command.type === 'cancel-paragraph' ||
        command.type === 'patch-paragraph' ||
        command.type === 'run-all-paragraphs' ||
        command.type === 'cancel-all-paragraphs' ||
        command.type === 'clear-all-paragraph-output'
      ) {
        return route.dispatchCommand?.(command) ?? false;
      }
      const requestedEvent =
        command.type === 'commit-paragraph'
          ? ({ type: 'paragraph-save-requested', paragraphId: command.paragraphId } as const)
          : ({ type: 'paragraph-run-requested', paragraphId: command.paragraphId } as const);
      const rejectedEvent =
        command.type === 'commit-paragraph'
          ? ({ type: 'paragraph-save-cancelled', paragraphId: command.paragraphId } as const)
          : ({ type: 'paragraph-run-rejected', paragraphId: command.paragraphId } as const);
      if (!apply(requestedEvent)) {
        const paragraph = state.paragraphsById[command.paragraphId];
        if (
          command.type === 'commit-paragraph' &&
          paragraph?.savePending &&
          paragraph.snapshot.text !== paragraph.savedText
        ) {
          queuedSaveRequests.add(command.paragraphId);
        }
        return false;
      }
      let dispatched = false;
      try {
        dispatched = route.dispatchCommand?.(command) ?? false;
      } catch {
        apply(rejectedEvent);
        return false;
      }
      if (!dispatched) {
        apply(rejectedEvent);
      }
      return dispatched;
    }
  });
  dispatchQueuedSave = port.dispatch;

  return Object.freeze({
    port,
    apply
  });
};
