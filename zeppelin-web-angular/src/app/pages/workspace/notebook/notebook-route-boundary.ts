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

import { inject, Type } from '@angular/core';
import { CanMatchFn, Router, Routes } from '@angular/router';

import { parseBooleanFlag } from '@zeppelin/services/query-flag.util';

export const NOTEBOOK_ROUTE_PATH = 'notebook';
export const NOTEBOOK_CHILD_ROUTE_PATHS = [':noteId', ':noteId/revision/:revisionId'] as const;

export const createNotebookChildRoutes = (component: Type<unknown>, canMatch?: CanMatchFn[]): Routes =>
  NOTEBOOK_CHILD_ROUTE_PATHS.map(path => (canMatch ? { path, component, canMatch } : { path, component }));

/**
 * Development and test opt-in for the private Core-backed read view (ZEPPELIN-6737), e.g.
 * `#/notebook/<id>?notebookCoreRead`. Without it the editable notebook is unchanged. Navigating to
 * a notebook URL without it leaves the read view and loads the editable notebook afresh.
 */
const NOTEBOOK_CORE_READ_QUERY_PARAM = 'notebookCoreRead';

export const canMatchNotebookCoreRead: CanMatchFn = () =>
  parseBooleanFlag(
    inject(Router).currentNavigation()?.extractedUrl.queryParamMap.get(NOTEBOOK_CORE_READ_QUERY_PARAM)
  ) === true;
