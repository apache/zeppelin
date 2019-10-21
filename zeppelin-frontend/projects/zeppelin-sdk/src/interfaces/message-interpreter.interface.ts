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
