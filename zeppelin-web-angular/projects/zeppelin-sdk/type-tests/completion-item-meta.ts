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

import { CompletionItem } from '../src/interfaces/message-paragraph.interface';

// Spark and Flink interpreters build InterpreterCompletion(name, value, null), and
// NotebookServer's Gson omits null fields, so their completion payloads have no `meta` key.
export const sparkFlinkCompletion: CompletionItem = { name: 'println', value: 'println' };

// `name` and `value` are always supplied by production construction sites and must stay required.
// @ts-expect-error `name` is required
export const missingName: CompletionItem = { value: 'println' };

// @ts-expect-error `value` is required
export const missingValue: CompletionItem = { name: 'println' };
