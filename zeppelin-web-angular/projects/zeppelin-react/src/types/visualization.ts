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

export enum DatasetType {
  TABLE = 'TABLE',
  TEXT = 'TEXT',
  HTML = 'HTML',
  IMG = 'IMG',
  ANGULAR = 'ANGULAR'
}

export type VisualizationMode =
  | 'table'
  | 'multiBarChart'
  | 'pieChart'
  | 'lineChart'
  | 'stackedAreaChart'
  | 'scatterChart';

export interface ParagraphResult {
  type: DatasetType;
  data: string;
}

export interface ParagraphConfig {
  graph?: {
    mode: VisualizationMode;
    optionOpen?: boolean;
    height?: number;
    keys?: Array<{ name: string; index: number; aggregation: string }>;
    groups?: Array<{ name: string; index: number; aggregation: string }>;
    values?: Array<{ name: string; index: number; aggregation: string }>;
    setting?: Record<string, any>;
    commonSetting?: any;
  };
}
