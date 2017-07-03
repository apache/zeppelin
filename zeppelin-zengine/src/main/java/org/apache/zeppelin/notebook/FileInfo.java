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

import org.apache.commons.lang.StringUtils;

/**
 * Represents note file info for storage 
 *
 */
public class FileInfo {

  private String folder;
  private String fileName;
  public static final FileInfo EMPTY = new FileInfo();
  
  private FileInfo() {
    folder = StringUtils.EMPTY;
    fileName = StringUtils.EMPTY;
  }
  
  public static FileInfo createInstance() {
    return new FileInfo();
  }
  
  public String getFile() {
    return fileName;
  }
  
  public String setFile(String filename) {
    return this.fileName = filename; 
  }
  
  public String getFolder() {
    return folder;
  }
  
  public String setFolder(String path) {
    return this.folder = path; 
  }
  
  public static boolean isEmpty(Object obj) {
    if (obj == null || !(obj instanceof FileInfo)) {
      return true;
    }
    FileInfo fi = (FileInfo) obj;
    return fi == EMPTY;
  }
  
  public FileInfo copy() {
    FileInfo copy = createInstance();
    copy.setFile(this.getFile());
    copy.setFolder(this.getFolder());
    return copy;
  }
  
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("FileInfo = [ file: ").append(fileName.toString())
    .append(", folder: ").append(folder.toString())
      .append(" ] ");
    
    return str.toString();
  }
}
