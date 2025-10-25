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

export interface TableData {
  columnNames: string[];
  rows: string[][];
}

export const parseTableData = (data: string): TableData => {
  const lines = data.trim().split('\n');
  if (lines.length === 0) return { columnNames: [], rows: [] };

  const columnNames = lines[0].split('\t');
  const rows = lines.slice(1).map(line => line.split('\t'));

  return { columnNames, rows };
};
