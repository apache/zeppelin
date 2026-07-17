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

// Single source of truth for the aggregation-type set shared by the table
// visualization and the pivot setting UI. Only the type is shared; each
// component keeps its own display-order array (typed readonly AggregationType[])
// so that neither aggregation dropdown changes its visible order.
export type AggregationType = 'count' | 'sum' | 'min' | 'max' | 'avg';
