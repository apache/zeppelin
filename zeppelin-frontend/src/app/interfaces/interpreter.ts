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
  proxyPort: string;
  proxyLogin: string;
  proxyPassword: string;
}

export interface InterpreterRepository {
  id: string;
  type: string;
  url: string;
  releasePolicy: ReleasePolicy;
  snapshotPolicy: SnapshotPolicy;
  // tslint:disable-next-line
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
    value: boolean;
    type: string;
    defaultValue?: string;
    description?: string;
  };
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

interface Option {
  remote: boolean;
  port: number;
  isExistingProcess: boolean;
  setPermission: boolean;
  // tslint:disable-next-line:no-any
  owners: any[];
  isUserImpersonate: boolean;
  perNote?: string;
  perUser?: string;
}
