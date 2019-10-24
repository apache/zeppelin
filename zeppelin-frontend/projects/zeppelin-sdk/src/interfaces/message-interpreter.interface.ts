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

export interface InterpreterSetting {
  interpreterSettings: InterpreterItem[];
}

export interface InterpreterItem {
  id: string;
  name: string;
  group: string;
  properties: Properties;
  status: string;
  interpreterGroup: InterpreterGroupItem[];
  dependencies: string[];
  option: Option;
}

export interface InterpreterBindings {
  interpreterBindings: InterpreterBindingItem[];
}

export interface InterpreterBindingItem {
  id: string;
  name: string;
  selected: boolean;
  interpreters: InterpreterGroupItem[];
}

interface Properties {
  [name: string]: {
    name: string;
    value: boolean;
    type: string;
  };
}

interface InterpreterGroupItem {
  name: string;
  class: string;
  defaultInterpreter: boolean;
  editor?: Editor;
}

interface Editor {
  language?: string;
  editOnDblClick?: boolean;
  completionKey?: string;
  completionSupport?: boolean;
}

interface Option {
  remote: boolean;
  port: number;
  isExistingProcess: boolean;
  setPermission: boolean;
  owners: string[];
  isUserImpersonate: boolean;
  perNote?: string;
  perUser?: string;
}
