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

import java.util.Map;

/**
 * Provide info about notes, folders and its relations
 */
public class NotesInfoProvider {
  private final Map<String, Note> notes;
  private final FolderView folders;

  public NotesInfoProvider(Map<String, Note> notes, FolderView folders) {
    this.notes = notes;
    this.folders = folders;
  }

  public Folder getFolderByNoteId(String noteId){
    return folders.getFolderOf(notes.get(noteId));
  }

  public Folder getFolder(String folderId){
    return folders.getFolder(folderId);
  }
}
