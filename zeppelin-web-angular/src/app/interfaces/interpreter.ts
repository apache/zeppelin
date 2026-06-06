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

export type InterpreterPropertyTypes = 'textarea' | 'string' | 'number' | 'url' | 'password' | 'checkbox';

export interface Interpreter {
  id: string;
  name: string;
  group: string;
  properties: Properties;
  status: string;
  errorReason?: string;
  interpreterGroup: InterpreterGroupItem[];
  dependencies: DependenciesItem[];
  option: Option;
}

export interface InterpreterMap {
  [key: string]: Interpreter;
}

export interface CreateInterpreterRepositoryForm {
  id: string;
  url: string;
  snapshot: boolean;
  username: string;
  password: string;
  proxyProtocol: string;
  proxyHost: string;
  proxyPort: string | null;
  proxyLogin: string;
  proxyPassword: string;
}

export interface InterpreterRepository {
  id: string;
  type: string;
  url: string;
  releasePolicy: ReleasePolicy;
  snapshotPolicy: SnapshotPolicy;
  // eslint-disable-next-line
  mirroredRepositories: any[];
  repositoryManager: boolean;
}
interface ReleasePolicy {
  enabled: boolean;
  updatePolicy: string;
  checksumPolicy: string;
}
interface SnapshotPolicy {
  enabled: boolean;
  updatePolicy: string;
  checksumPolicy: string;
}

interface Properties {
  [key: string]: {
    name: string;
    // The server serializes every property value as a string or boolean.
    // `type: 'number'` props are still sent as quoted strings (e.g. "1000") —
    // the type is only a UI hint, not the JSON type. null is never sent either:
    // Gson omits null values, so an unset value arrives as undefined (key absent).
    value: string | boolean;
    type: InterpreterPropertyTypes;
    defaultValue?: string | boolean;
    description?: string;
  };
}

export interface InterpreterPropertyValue {
  name: string;
  value: string | boolean;
  type: InterpreterPropertyTypes;
}

/**
 * Request shape for creating/updating an interpreter setting.
 * Mirrors the fields the server actually reads (InterpreterOption.java) —
 * UI-only state such as `session`/`process` must not be sent.
 */
export interface InterpreterSettingOption {
  isExistingProcess: boolean;
  isUserImpersonate: boolean;
  owners: string[];
  perNote: string;
  perUser: string;
  /** null is accepted by the server and kept as the default -1 (unset) */
  port: number | null;
  host: string;
  remote: boolean;
  setPermission: boolean;
}

export interface InterpreterSettingRequest {
  name: string;
  group: string;
  option: InterpreterSettingOption;
  properties: Record<string, InterpreterPropertyValue>;
  dependencies: Array<{
    groupArtifactVersion: string;
    exclusions: string[];
  }>;
}

interface InterpreterGroupItem {
  name: string;
  class: string;
  defaultInterpreter: boolean;
  editor: Editor;
}
interface Editor {
  language: string;
  editOnDblClick: boolean;
  completionKey?: string;
  completionSupport?: boolean;
}

interface DependenciesItem {
  groupArtifactVersion: string;
  local: boolean;
  exclusions: string[];
}

/**
 * Response shape of InterpreterOption.java serialized by Gson.
 * Primitive boolean/int fields are always present; String/List fields
 * are omitted when null (e.g. fresh option templates from GET /interpreter).
 */
interface Option {
  remote: boolean;
  port: number;
  isExistingProcess: boolean;
  setPermission: boolean;
  isUserImpersonate: boolean;
  host?: string;
  owners?: string[];
  perNote?: string;
  perUser?: string;
}
