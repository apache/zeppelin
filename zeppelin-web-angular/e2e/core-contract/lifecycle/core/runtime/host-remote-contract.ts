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

export type NotebookParagraphStatus = 'UNKNOWN' | 'READY' | 'PENDING' | 'RUNNING' | 'FINISHED' | 'ERROR' | 'ABORT';

export type NotebookParagraphResultType = 'TEXT' | 'HTML' | 'TABLE' | 'IMG' | 'ANGULAR' | string;

export type NotebookVisualizationMode =
  | 'table'
  | 'multiBarChart'
  | 'pieChart'
  | 'lineChart'
  | 'stackedAreaChart'
  | 'scatterChart'
  | string;

export type NotebookParagraphResult = Readonly<{
  type: NotebookParagraphResultType;
  data: string;
}>;

export type NotebookParagraphResultConfig = Readonly<{
  graph: Readonly<object> & Readonly<{ mode?: NotebookVisualizationMode }>;
}>;

export type NotebookParagraphResultConfigs = Readonly<Record<string, NotebookParagraphResultConfig>>;

export type NotebookFormValue = string | readonly string[];

export type NotebookDynamicForm = Readonly<{
  defaultValue: NotebookFormValue;
  hidden: boolean;
  name: string;
  displayName?: string;
  type: string;
  argument?: string;
  options?: readonly Readonly<{ value: string; displayName?: string }>[];
}>;

export type NotebookDynamicForms = Readonly<Record<string, NotebookDynamicForm>>;

export type NotebookFormParams = Readonly<Record<string, NotebookFormValue>>;

export type NotebookRevision = Readonly<{
  id?: string;
  message: string;
  time?: number;
}>;

export type NotebookSchedule = Readonly<{
  cron?: string;
  releaseResource: boolean;
}>;

export type NotebookLookAndFeel = 'report' | 'default' | 'simple';

export type NotebookPermissions = Readonly<{
  readers: readonly string[];
  owners: readonly string[];
  writers: readonly string[];
  runners: readonly string[];
}>;

export type NotebookRevisionParagraph = Readonly<{
  id: string;
  text: string;
  title?: string;
}>;

export type NotebookRevisionComparison = Readonly<{
  firstRevisionId: string;
  secondRevisionId: string;
  firstParagraphs: readonly NotebookRevisionParagraph[];
  secondParagraphs: readonly NotebookRevisionParagraph[];
}>;

export type NotebookInterpreterBinding = Readonly<{
  id: string;
  name: string;
  selected: boolean;
}>;

export type NotebookParagraphSnapshot = Readonly<{
  id: string;
  text: string;
  status: NotebookParagraphStatus;
  language?: string;
  progress: number;
  isDirty: boolean;
  results?: readonly NotebookParagraphResult[];
  resultConfigs?: NotebookParagraphResultConfigs;
}>;

export type NotebookParagraphInput = Omit<NotebookParagraphSnapshot, 'isDirty' | 'progress'> &
  Readonly<{ progress?: number }>;

export type NotebookCorePhase = 'idle' | 'loading' | 'ready' | 'error';

export type NotebookCoreSnapshot = Readonly<{
  version: number;
  noteId: string;
  revisionId: string | null;
  phase: NotebookCorePhase;
  title: string | null;
  noteForms: NotebookDynamicForms;
  noteParams: NotebookFormParams;
  permissions?: NotebookPermissions;
  collaborativeUsers?: readonly string[];
  scheduler?: NotebookSchedule;
  revisions?: readonly NotebookRevision[];
  lookAndFeel?: NotebookLookAndFeel;
  personalizedMode?: boolean;
  paragraphs: readonly NotebookParagraphSnapshot[];
  error: string | null;
}>;

export type NotebookCoreUnsubscribe = () => void;

export type NotebookCoreSnapshotListener = () => void;

export type NotebookCoreCommand =
  | Readonly<{ type: 'run-paragraph'; paragraphId: string }>
  | Readonly<{ type: 'cancel-paragraph'; paragraphId: string }>
  | Readonly<{ type: 'run-all-paragraphs' }>
  | Readonly<{ type: 'cancel-all-paragraphs' }>
  | Readonly<{ type: 'clear-all-paragraph-output' }>
  | Readonly<{ type: 'commit-paragraph'; paragraphId: string }>
  | Readonly<{ type: 'patch-paragraph'; paragraphId: string; patch: string }>;

export type NotebookCoreCommandHandler = (command: NotebookCoreCommand) => boolean;

export type NotebookCorePort = Readonly<{
  getSnapshot: () => NotebookCoreSnapshot;
  subscribe: (listener: NotebookCoreSnapshotListener) => NotebookCoreUnsubscribe;
  dispatch: NotebookCoreCommandHandler;
}>;

export type NotebookCoreRemoteProps = Readonly<{
  core: NotebookCorePort;
  readOnly?: boolean;
  canEdit?: boolean;
  canRun?: boolean;
  onParagraphTextChange?: (paragraphId: string, text: string) => void;
  onParagraphInsert?: (index: number) => void;
  onParagraphRemove?: (paragraphId: string) => void;
  onParagraphMove?: (paragraphId: string, index: number) => void;
  onNotebookTitleChange?: (title: string) => void;
  onCloneNotebook?: () => void;
  onExportNotebook?: () => void;
  onReloadNotebook?: () => void;
  canTogglePersonalizedMode?: boolean;
  personalizedMode?: boolean;
  onTogglePersonalizedMode?: () => void;
  canDeleteNotebook?: boolean;
  isTrashedNotebook?: boolean;
  onDeleteNotebook?: () => void;
  lookAndFeel?: NotebookLookAndFeel;
  onLookAndFeelChange?: (lookAndFeel: NotebookLookAndFeel) => void;
  onShowShortcut?: () => void;
  revisions?: readonly NotebookRevision[];
  currentRevision?: string;
  revisionView?: boolean;
  onRevisionSelect?: (revisionId: string) => void;
  onCheckpointNotebook?: (message: string) => void;
  onSetNotebookRevision?: () => void;
  onRevisionCompare?: (firstRevisionId: string, secondRevisionId: string) => Promise<NotebookRevisionComparison>;
  interpreterBindings?: readonly NotebookInterpreterBinding[];
  onInterpreterBindingsChange?: (bindingIds: readonly string[]) => void;
  scheduler?: NotebookSchedule;
  canSchedule?: boolean;
  onScheduleChange?: (schedule: NotebookSchedule) => void;
  collaborativeUsers?: readonly string[];
  canManagePermissions?: boolean;
  onPermissionsChange?: (permissions: NotebookPermissions) => Promise<void> | void;
  onExtensionChange?: (extension: 'interpreter' | 'permissions' | 'revisions' | 'hide') => void;
  onNoteFormsChange?: (params: NotebookFormParams) => void;
  onParagraphResultConfigChange?: (
    paragraphId: string,
    resultIndex: number,
    config: NotebookParagraphResultConfigs[string]
  ) => void;
}>;
