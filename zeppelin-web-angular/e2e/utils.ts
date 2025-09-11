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

import { test, TestInfo } from '@playwright/test';

export const PAGES = {
  // Main App
  APP: 'src/app/app.component',

  // Core
  CORE: {
    DESTROY_HOOK: 'src/app/core/destroy-hook/destroy-hook.component'
  },

  // Pages
  PAGES: {
    LOGIN: 'src/app/pages/login/login.component'
  },

  // Pages - Workspace
  WORKSPACE: {
    MAIN: 'src/app/pages/workspace/workspace.component',
    CONFIGURATION: 'src/app/pages/workspace/configuration/configuration.component',
    CREDENTIAL: 'src/app/pages/workspace/credential/credential.component',
    HOME: 'src/app/pages/workspace/home/home.component',
    INTERPRETER: 'src/app/pages/workspace/interpreter/interpreter.component',
    INTERPRETER_CREATE_REPO:
      'src/app/pages/workspace/interpreter/create-repository-modal/create-repository-modal.component',
    INTERPRETER_ITEM: 'src/app/pages/workspace/interpreter/item/item.component',
    JOB_MANAGER: 'src/app/pages/workspace/job-manager/job-manager.component',
    JOB_STATUS: 'src/app/pages/workspace/job-manager/job-status/job-status.component',
    JOB: 'src/app/pages/workspace/job-manager/job/job.component',
    NOTEBOOK_REPOS: 'src/app/pages/workspace/notebook-repos/notebook-repos.component',
    NOTEBOOK_REPOS_ITEM: 'src/app/pages/workspace/notebook-repos/item/item.component',
    NOTEBOOK_SEARCH: 'src/app/pages/workspace/notebook-search/notebook-search.component',
    NOTEBOOK_SEARCH_RESULT: 'src/app/pages/workspace/notebook-search/result-item/result-item.component',
    NOTEBOOK: 'src/app/pages/workspace/notebook/notebook.component',
    NOTEBOOK_ACTION_BAR: 'src/app/pages/workspace/notebook/action-bar/action-bar.component',
    NOTEBOOK_ADD_PARAGRAPH: 'src/app/pages/workspace/notebook/add-paragraph/add-paragraph.component',
    NOTEBOOK_INTERPRETER_BINDING: 'src/app/pages/workspace/notebook/interpreter-binding/interpreter-binding.component',
    NOTEBOOK_NOTE_FORM: 'src/app/pages/workspace/notebook/note-form-block/note-form-block.component',
    NOTEBOOK_PERMISSIONS: 'src/app/pages/workspace/notebook/permissions/permissions.component',
    NOTEBOOK_REVISIONS: 'src/app/pages/workspace/notebook/revisions-comparator/revisions-comparator.component',
    NOTEBOOK_ELASTIC_INPUT: 'src/app/pages/workspace/notebook/share/elastic-input/elastic-input.component',
    NOTEBOOK_SIDEBAR: 'src/app/pages/workspace/notebook/sidebar/sidebar.component',
    NOTEBOOK_PARAGRAPH: 'src/app/pages/workspace/notebook/paragraph/paragraph.component',
    NOTEBOOK_PARAGRAPH_CODE_EDITOR: 'src/app/pages/workspace/notebook/paragraph/code-editor/code-editor.component',
    NOTEBOOK_PARAGRAPH_CONTROL: 'src/app/pages/workspace/notebook/paragraph/control/control.component',
    NOTEBOOK_PARAGRAPH_FOOTER: 'src/app/pages/workspace/notebook/paragraph/footer/footer.component',
    NOTEBOOK_PARAGRAPH_PROGRESS: 'src/app/pages/workspace/notebook/paragraph/progress/progress.component',
    PUBLISHED_PARAGRAPH: 'src/app/pages/workspace/published/paragraph/paragraph.component',
    SHARE_DYNAMIC_FORMS: 'src/app/pages/workspace/share/dynamic-forms/dynamic-forms.component',
    SHARE_RESULT: 'src/app/pages/workspace/share/result/result.component'
  },

  // Share
  SHARE: {
    ABOUT_ZEPPELIN: 'src/app/share/about-zeppelin/about-zeppelin.component',
    CODE_EDITOR: 'src/app/share/code-editor/code-editor.component',
    FOLDER_RENAME: 'src/app/share/folder-rename/folder-rename.component',
    HEADER: 'src/app/share/header/header.component',
    NODE_LIST: 'src/app/share/node-list/node-list.component',
    NOTE_CREATE: 'src/app/share/note-create/note-create.component',
    NOTE_IMPORT: 'src/app/share/note-import/note-import.component',
    NOTE_RENAME: 'src/app/share/note-rename/note-rename.component',
    NOTE_TOC: 'src/app/share/note-toc/note-toc.component',
    PAGE_HEADER: 'src/app/share/page-header/page-header.component',
    RESIZE_HANDLE: 'src/app/share/resize-handle/resize-handle.component',
    SHORTCUT: 'src/app/share/shortcut/shortcut.component',
    SPIN: 'src/app/share/spin/spin.component'
  },

  // Visualizations
  VISUALIZATIONS: {
    AREA_CHART: 'src/app/visualizations/area-chart/area-chart-visualization.component',
    BAR_CHART: 'src/app/visualizations/bar-chart/bar-chart-visualization.component',
    LINE_CHART: 'src/app/visualizations/line-chart/line-chart-visualization.component',
    PIE_CHART: 'src/app/visualizations/pie-chart/pie-chart-visualization.component',
    SCATTER_CHART: 'src/app/visualizations/scatter-chart/scatter-chart-visualization.component',
    TABLE: 'src/app/visualizations/table/table-visualization.component',
    COMMON: {
      PIVOT_SETTING: 'src/app/visualizations/common/pivot-setting/pivot-setting.component',
      SCATTER_SETTING: 'src/app/visualizations/common/scatter-setting/scatter-setting.component',
      X_AXIS_SETTING: 'src/app/visualizations/common/x-axis-setting/x-axis-setting.component'
    }
  },

  // Projects
  PROJECTS: {
    JSON_VIS: 'projects/helium-vis-example/src/json-vis.component'
  }
} as const;

export function testPage(pageName: string, testInfo: TestInfo) {
  testInfo.annotations.push({
    type: 'page',
    description: pageName
  });
}

export function testPageBeforeEach(pageName: string) {
  test.beforeEach(async ({}, testInfo) => {
    testPage(pageName, testInfo);
  });
}

interface PageStructureType {
  [key: string]: string | PageStructureType;
}

export function flattenPageComponents(pages: PageStructureType): string[] {
  const result: string[] = [];

  function flatten(obj: PageStructureType) {
    for (const key in obj) {
      if (typeof obj[key] === 'string') {
        result.push(obj[key]);
      } else if (typeof obj[key] === 'object' && obj[key] !== null) {
        flatten(obj[key]);
      }
    }
  }

  flatten(pages);
  return result.sort();
}

export function getCoverageTransformPaths(): string[] {
  return flattenPageComponents(PAGES);
}
