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

import { InjectionToken } from '@angular/core';
import { SafeUrl } from '@angular/platform-browser';
import { editor } from 'monaco-editor';
import IEditorConstructionOptions = editor.IEditorConstructionOptions;
import IDiffEditorConstructionOptions = editor.IDiffEditorConstructionOptions;

export type EditorOptions = IEditorConstructionOptions;
export type DiffEditorOptions = IDiffEditorConstructionOptions;
export type JoinedEditorOptions = EditorOptions | DiffEditorOptions;

export type NzEditorMode = 'normal' | 'diff';

export enum NzCodeEditorLoadingStatus {
  UNLOAD = 'unload',
  LOADING = 'loading',
  LOADED = 'LOADED'
}

export interface NzCodeEditorConfig {
  assetsRoot?: string | SafeUrl;
  defaultEditorOption?: JoinedEditorOptions;
  onLoad?(): void;
  onFirstEditorInit?(): void;
  onInit?(): void;
}

export const NZ_CODE_EDITOR_CONFIG = new InjectionToken<NzCodeEditorConfig>('nz-code-editor-config', {
  providedIn: 'root',
  factory: NZ_CODE_EDITOR_CONFIG_FACTORY
});

export function NZ_CODE_EDITOR_CONFIG_FACTORY(): NzCodeEditorConfig {
  return {};
}
