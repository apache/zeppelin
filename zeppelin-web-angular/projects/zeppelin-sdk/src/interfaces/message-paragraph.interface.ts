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

import { EditorCompletionKey, EditorLanguage, EditorMode } from './message-common.interface';

export enum DynamicFormsType {
  TextBox = 'TextBox',
  Password = 'Password',
  Select = 'Select',
  CheckBox = 'CheckBox'
}

export interface DynamicFormsItem {
  defaultValue: string | string[];
  hidden: boolean;
  name: string;
  displayName?: string;
  type: DynamicFormsType;
  argument?: string;
  options?: Array<{ value: string; displayName?: string }>;
}

export interface DynamicForms {
  [key: string]: DynamicFormsItem;
}

export interface DynamicFormParams {
  [key: string]: string | string[];
}

export interface ParagraphEditorSetting {
  language?: EditorLanguage;
  editOnDblClick?: boolean;
  isOutputHidden?: boolean;
  completionKey?: EditorCompletionKey;
  completionSupport?: boolean;
  params?: DynamicFormParams;
  forms?: DynamicForms;
}

// TODO(hsuanxyz)
export interface ParagraphParams {
  // tslint:disable-next-line no-any
  [key: string]: any;
}

export interface ParagraphConfigResults {
  [index: string]: ParagraphConfigResult;
}

export interface ParagraphConfigResult {
  graph: GraphConfig;
}

export interface ParagraphConfig {
  editorSetting?: ParagraphEditorSetting;
  colWidth?: number;
  editorMode?: EditorMode;
  fontSize?: number;
  results?: ParagraphConfigResults;
  enabled?: boolean;
  tableHide?: boolean;
  lineNumbers?: boolean;
  editorHide?: boolean;
  title?: boolean;
  runOnSelectionChange?: boolean;
  isZeppelinNotebookCronEnable?: boolean;
}

export interface ParagraphResults {
  code?: string;
  msg?: ParagraphIResultsMsgItem[];

  [index: number]: {};
}

export enum DatasetType {
  NETWORK = 'NETWORK',
  TABLE = 'TABLE',
  HTML = 'HTML',
  TEXT = 'TEXT',
  ANGULAR = 'ANGULAR',
  IMG = 'IMG'
}

export class ParagraphIResultsMsgItem {
  type: DatasetType = DatasetType.TEXT;
  data = '';
}

export interface ParasInfo {
  id: string;
  infos: RuntimeInfos;
}

export interface RuntimeInfos {
  jobUrl: RuntimeInfosJobUrl;
}

interface RuntimeInfosJobUrl {
  propertyName: string;
  label: string;
  tooltip: string;
  group: string;
  values: RuntimeInfosValuesItem[];
  interpreterSettingId: string;
}

interface RuntimeInfosValuesItem {
  jobUrl: string;
}

export interface ParagraphItem {
  text: string;
  user: string;
  dateUpdated: string;
  config: ParagraphConfig;
  settings: ParagraphEditorSetting;
  results?: ParagraphResults;
  // tslint:disable-next-line no-any
  apps: any[];
  progressUpdateIntervalMs: number;
  jobName: string;
  id: string;
  dateCreated: string;
  dateStarted?: string;
  dateFinished?: string;
  errorMessage?: string;
  runtimeInfos?: RuntimeInfos;
  status: string;
  title?: string;
  focus?: boolean;
  // tslint:disable-next-line no-any TODO(hsuanxyz)
  aborted: any;
  // tslint:disable-next-line no-any TODO(hsuanxyz)
  lineNumbers: any;
  // tslint:disable-next-line no-any TODO(hsuanxyz)
  fontSize: any;
}

export interface SendParagraph {
  id: string;
  title?: string;
  paragraph: string;
  config: ParagraphConfig;
  params: ParagraphParams;
}

export interface CopyParagraph {
  index: number;
  title?: string;
  paragraph: string;
  config: ParagraphConfig;
  params: ParagraphParams;
}

export interface RunParagraph extends SendParagraph {
  // tslint:disable-next-line no-any
  [key: string]: any;
}

export interface CommitParagraph extends SendParagraph {
  noteId: string;
}

export interface RunAllParagraphs {
  noteId: string;
  paragraphs: string;
}

export interface InsertParagraph {
  index: number;
}

export interface MoveParagraph {
  id: string;
  index: number;
}

export interface AngularObjectUpdated {
  noteId: string;
  paragraphId: string;
  name: string;
  value: string;
  interpreterGroupId: string;
}

export interface AngularObjectRemove {
  noteId: string;
  paragraphId: string;
  name: string;
}

export interface AngularObjectUpdate {
  noteId: string;
  paragraphId: string;
  interpreterGroupId: string;
  angularObject: {
    name: string;
    // tslint:disable-next-line:no-any
    object: any;
    noteId: string;
    paragraphId: string;
  };
}

export interface AngularObjectClientBind {
  noteId: string;
  name: string;
  value: string;
  paragraphId: string;
}

export interface AngularObjectClientUnbind {
  noteId: string;
  name: string;
  paragraphId: string;
}

export interface CancelParagraph {
  id: string;
}

export interface ParagraphRemove {
  id: string;
}

export interface ParagraphClearOutput {
  id: string;
}

export interface ParagraphClearAllOutput {
  id: string;
}

export interface Completion {
  id: string;
  buf: string;
  cursor: number;
}

export interface CompletionItem {
  meta: string;
  value: string;
  name: string;
}

export interface CompletionReceived {
  completions: CompletionItem[];
  id: string;
}

export interface PatchParagraphReceived {
  id: string;
  noteId: string;
  patch: string;
}

export interface PatchParagraphSend {
  paragraphId: string;
  patch: string;
}

export interface ParagraphRemoved {
  id: string;
}

export type VisualizationMode =
  | 'table'
  | 'lineChart'
  | 'stackedAreaChart'
  | 'multiBarChart'
  | 'scatterChart'
  | 'pieChart'
  | string;

export class GraphConfig {
  mode: VisualizationMode = 'table';
  height = 300;
  optionOpen = false;
  setting: GraphConfigSetting = {};
  keys: GraphConfigKeysItem[] = [];
  groups: GraphConfigGroupsItem[] = [];
  values: GraphConfigValuesItem[] = [];
  commonSetting: GraphConfigCommonSetting;
}

export interface Progress {
  id: string;
  progress: number;
}

interface GraphConfigSetting {
  table?: VisualizationTable;
  lineChart?: VisualizationLineChart;
  stackedAreaChart?: VisualizationStackedAreaChart;
  multiBarChart?: VisualizationMultiBarChart;
  scatterChart?: VisualizationScatterChart;
}

interface VisualizationTable {
  tableGridState: TableGridState;
  tableColumnTypeState: TableColumnTypeState;
  updated: boolean;
  initialized: boolean;
  tableOptionSpecHash: string;
  tableOptionValue: TableOptionValue;
}

interface TableGridState {
  columns: ColumnsItem[];
  scrollFocus: ScrollFocus;
  // tslint:disable-next-line
  selection: any[];
  grouping: Grouping;
  treeView: TreeView;
  pagination: Pagination;
}

interface ColumnsItem {
  name: string;
  visible: boolean;
  width: string;
  sort: Sort;
  filters: FiltersItem[];
  pinned: string;
}

interface Sort {
  // tslint:disable-next-line
  [key: string]: any;
}

interface FiltersItem {
  // tslint:disable-next-line
  [key: string]: any;
}

interface ScrollFocus {
  // tslint:disable-next-line
  [key: string]: any;
}

interface Grouping {
  // tslint:disable-next-line
  grouping: any[];
  // tslint:disable-next-line
  aggregations: any[];
  rowExpandedStates: RowExpandedStates;
}

interface RowExpandedStates {
  // tslint:disable-next-line
  [key: string]: any;
}

interface TreeView {
  // tslint:disable-next-line
  [key: string]: any;
}

interface Pagination {
  paginationCurrentPage: number;
  paginationPageSize: number;
}

interface TableColumnTypeState {
  updated: boolean;
  names: Names;
}

interface Names {
  index: string;
  value: string;
  random: string;
  count: string;
}

interface TableOptionValue {
  useFilter: boolean;
  showPagination: boolean;
  showAggregationFooter: boolean;
}

export type XLabelStatus = 'default' | 'rotate' | 'hide';

export class XAxisSetting {
  rotate = { degree: '-45' };
  xLabelStatus: XLabelStatus = 'default';
}

export class VisualizationLineChart extends XAxisSetting {
  forceY = false;
  lineWithFocus = false;
  isDateFormat = false;
  dateFormat = '';
}

export class VisualizationStackedAreaChart extends XAxisSetting {
  style: 'stream' | 'expand' | 'stack' = 'stack';
}

export class VisualizationMultiBarChart extends XAxisSetting {
  stacked = false;
}

export class VisualizationScatterChart {
  xAxis?: XAxis;
  yAxis?: YAxis;
  group?: Group;
  size?: Size;
}

interface XAxis {
  name: string;
  index: number;
  aggr: string;
}

interface YAxis {
  name: string;
  index: number;
  aggr: string;
}

interface Group {
  name: string;
  index: number;
  aggr: string;
}

interface Size {
  name: string;
  index: number;
  aggr: string;
}

interface GraphConfigKeysItem {
  name: string;
  index: number;
  aggr: string;
}

interface GraphConfigGroupsItem {
  name: string;
  index: number;
  aggr: string;
}

interface GraphConfigValuesItem {
  name: string;
  index: number;
  aggr: string;
}

interface GraphConfigCommonSetting {
  // tslint:disable-next-line
  [key: string]: any;
}
