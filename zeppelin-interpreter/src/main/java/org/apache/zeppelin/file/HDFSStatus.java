/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

public class HDFSStatus {
  /**
   * Status of one file
   *
   * matches returned JSON
   */
  public class OneFileStatus {
    public long accessTime;
    public int blockSize;
    public int childrenNum;
    public int fileId;
    public String group;
    public long length;
    public long modificationTime;
    public String owner;
    public String pathSuffix;
    public String permission;
    public int replication;
    public int storagePolicy;
    public String type;
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("\nAccessTime = ").append(accessTime);
      sb.append("\nBlockSize = ").append(blockSize);
      sb.append("\nChildrenNum = ").append(childrenNum);
      sb.append("\nFileId = ").append(fileId);
      sb.append("\nGroup = ").append(group);
      sb.append("\nLength = ").append(length);
      sb.append("\nModificationTime = ").append(modificationTime);
      sb.append("\nOwner = ").append(owner);
      sb.append("\nPathSuffix = ").append(pathSuffix);
      sb.append("\nPermission = ").append(permission);
      sb.append("\nReplication = ").append(replication);
      sb.append("\nStoragePolicy = ").append(storagePolicy);
      sb.append("\nType = ").append(type);
      return sb.toString();
    }
  }

  /**
   * Status of one file
   *
   * matches returned JSON
   */
  public class SingleFileStatus {
    public OneFileStatus FileStatus;
  }

  /**
   * Status of all files in a directory
   *
   * matches returned JSON
   */
  public class MultiFileStatus {
    public OneFileStatus[] FileStatus;
  }

  /**
   * Status of all files in a directory
   *
   * matches returned JSON
   */
  public class AllFileStatus {
    public MultiFileStatus FileStatuses;
  }


}


