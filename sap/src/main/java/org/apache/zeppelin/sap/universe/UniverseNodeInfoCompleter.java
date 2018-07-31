/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.sap.universe;

import jline.console.completer.Completer;
import jline.internal.Preconditions;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * Case-insensitive completer.
 */
public class UniverseNodeInfoCompleter implements Completer {
  private final UniverseInfoTreeNode tree = new UniverseInfoTreeNode();

  public UniverseNodeInfoCompleter() {
  }

  public UniverseNodeInfoCompleter(final Collection<UniverseNodeInfo> nodes) {
    Preconditions.checkNotNull(nodes);
    for (UniverseNodeInfo node : nodes) {
      String folder = node.getFolder();
      if (StringUtils.isBlank(folder)) {
        tree.putInfo(node);
      } else {
        String[] path = folder.split("\\\\");
        UniverseInfoTreeNode universeInfoTreeNode = tree;
        for (String s : path) {
          if (!universeInfoTreeNode.contains(s)) {
            universeInfoTreeNode = universeInfoTreeNode.putFolder(s);
          } else {
            universeInfoTreeNode = universeInfoTreeNode.getFolder(s);
          }
        }
        universeInfoTreeNode.putInfo(node);
      }
    }
  }

  public int complete(final String buffer, final int cursor, final List candidates) {
    return completeCollection(buffer, cursor, candidates);
  }

  private int completeCollection(final String buffer, final int cursor,
      final Collection candidates) {
    Preconditions.checkNotNull(candidates);
    if (buffer == null) {
      candidates.addAll(tree.getNodesInfo());
    } else {
      String part = buffer.substring(0, cursor);
      List<String> path = new ArrayList<>();
      path.addAll(Arrays.asList(part.split("\\]\\.\\[")));
      if (part.endsWith(UniverseCompleter.START_NAME.toString())) {
        path.add(StringUtils.EMPTY);
      }

      UniverseInfoTreeNode treeNode = tree;
      for (int i = 0; i < path.size() - 1; i++) {
        String folder = cleanName(path.get(i));
        if (treeNode.contains(folder)) {
          treeNode = treeNode.getFolder(folder);
          if (treeNode == null) {
            break;
          }
        }
      }
      String p = cleanName(path.get(path.size() - 1)).toUpperCase();
      if (treeNode != null && treeNode.getChildren() != null) {
        if (p.isEmpty()) {
          candidates.addAll(treeNode.getNodesInfo());
        } else {
          for (UniverseNodeInfo universeNodeInfo : treeNode.getNodesInfo()) {
            if (universeNodeInfo.getName().toUpperCase().startsWith(p)) {
              candidates.add(universeNodeInfo);
            }
          }
        }
      }
    }

    return candidates.isEmpty() ? -1 : 0;
  }

  private String cleanName(String name) {
    return name.replaceAll(UniverseCompleter.CLEAN_NAME_REGEX, StringUtils.EMPTY);
  }

  private class UniverseInfoTreeNode {
    private String name;
    private boolean isFolder;
    private Map<String, Object> children;

    public UniverseInfoTreeNode() {
      this.name = "/";
      this.isFolder = true;
      this.children = new HashMap<>();
    }

    public UniverseInfoTreeNode(String name) {
      this.name = name;
      this.isFolder = true;
      this.children = new HashMap<>();
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isFolder() {
      return isFolder;
    }

    public void setFolder(boolean folder) {
      isFolder = folder;
    }

    public Map<String, Object> getChildren() {
      return children;
    }

    public void setChildren(Map<String, Object> children) {
      this.children = children;
    }

    public boolean contains(String name) {
      return children.containsKey(name);
    }

    public UniverseInfoTreeNode getFolder(String name) {
      Object child = children.get(name);
      if (child instanceof UniverseInfoTreeNode) {
        return (UniverseInfoTreeNode) children.get(name);
      }

      return null;
    }

    public UniverseInfoTreeNode putFolder(String name) {
      UniverseInfoTreeNode newNode = new UniverseInfoTreeNode(name);
      children.put(name, newNode);
      return newNode;
    }

    public void putInfo(UniverseNodeInfo info) {
      children.put(info.getId(), info);
    }

    public Collection<UniverseNodeInfo> getNodesInfo() {
      HashMap<String, UniverseNodeInfo> map = new HashMap<>();
      if (children != null) {
        for (Object o : children.values()) {
          if (o instanceof UniverseNodeInfo) {
            final UniverseNodeInfo nodeInfo = (UniverseNodeInfo) o;
            map.put(nodeInfo.getName(), nodeInfo);
          } else {
            final UniverseInfoTreeNode treeNode = (UniverseInfoTreeNode) o;
            final UniverseNodeInfo nodeInfo =
                new UniverseNodeInfo(treeNode.getName(), UniverseCompleter.TYPE_FOLDER);
            if (!map.containsKey(nodeInfo.getName())) {
              map.put(nodeInfo.getName(), nodeInfo);
            }
          }
        }
      }
      return map.values();
    }
  }
}
