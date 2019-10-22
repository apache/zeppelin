export interface NodeList {
  root: RootNode;
  flatList: FlatListNodeItem[];
  flatFolderMap: FlatFolderNodeMap;
}

export interface RootNode {
  children: NodeItem[];
}

export interface NodeItem {
  id: string;
  title: string;
  isLeaf?: boolean;
  expanded?: boolean;
  children?: NodeItem[];
  isTrash: boolean;
  path?: string;
}

interface FlatListNodeItem {
  id: string;
  path: string;
  isTrash: boolean;
}

interface FlatFolderNodeMap {
  [title: string]: NodeItem;
}
