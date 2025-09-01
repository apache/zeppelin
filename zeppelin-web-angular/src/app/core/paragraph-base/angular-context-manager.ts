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

import { Observable } from 'rxjs';

export interface AngularContext {
  paragraphId: string;
  key: string;
  // tslint:disable-next-line:no-any
  value: any;
  emit: boolean;
  set: boolean;
}

export interface AngularContextManager {
  setContextValue(key: string, value: unknown, paragraphId: string, emit?: boolean): void;
  unsetContextValue(key: string, paragraphId: string, emit?: boolean): unknown;
  contextChanged(): Observable<AngularContext>;
  runParagraphAction(): Observable<string>;
}
