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
package org.apache.zeppelin.notebook;

import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;

/**
 * Represents note file info for storage 
 *
 */
public class FileInfo {

  private ConcurrentMap<String, String> fileName;
  private boolean forceRename;
  public static final FileInfo EMPTY = new FileInfo();
  
  public FileInfo() {
    fileName = Maps.newConcurrentMap();
    forceRename = false;
  }
  
  public String getFileName(String storageName) {
    if (!fileName.containsKey(storageName)) {
      return StringUtils.EMPTY;
    }
    return fileName.get(storageName);
  }
  
  public String setFileName(String storageName, String filename) {
    return fileName.put(storageName, filename);
  }
  
  public boolean isForceRename() {
    return forceRename;
  }
  
  public void setForceRename(boolean triggerRename) {
    forceRename = triggerRename;
  }
  
  public static boolean isEmpty(FileInfo obj) {
    if (obj == null || !(obj instanceof FileInfo)) {
      return true;
    }
    return obj == EMPTY;
  }
  
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("FileInfo = [ fileName: ").append(fileName.toString())
      .append(", ").append("forceRename: ").append(forceRename)
      .append(" ] ");
    
    return str.toString();
  }
}
