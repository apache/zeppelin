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

package org.apache.zeppelin.notebook.repo;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notebook repository sync with remote storage
 */
public class NotebookRepoSync implements NotebookRepo{
  private VFSNotebookRepo localRepo;
  private NotebookRepo remoteRepo;
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoSync.class);

  /**
   * @param (conf)
   * @throws - Exception
   */
  public NotebookRepoSync(ZeppelinConfiguration conf) throws Exception {
    localRepo = new VFSNotebookRepo(conf);
    
    if (conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE).trim().length() != 0 &&
          !conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE).equals(
            "org.apache.zeppelin.notebook.repo.VFSNotebookRepo")) {
      Class<?> notebookStorageClass = getClass().forName(
                conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE));
      Constructor<?> constructor = notebookStorageClass.getConstructor(
                ZeppelinConfiguration.class);
      remoteRepo = (NotebookRepo) constructor.newInstance(conf);
      syncSimple();
    } else {
      remoteRepo = null;
    }

  }

  public List<NoteInfo> list() throws IOException {
    return localRepo.list();
  }

  public Note get(String noteId) throws IOException {
    return localRepo.get(noteId);
  }

  public void save(Note note) throws IOException {
    localRepo.save(note);
    if (remoteRepo != null) remoteRepo.save(note);
  }

  public void remove(String noteId) throws IOException {
    localRepo.remove(noteId);
    if (remoteRepo != null) remoteRepo.remove(noteId);
  }

  /**
   * sync for simple case of same token/instance 
   * @throws IOException
   */
  public void syncSimple() throws IOException {
    LOG.info("Sync start");
    List <NoteInfo> localNotes = localRepo.list();
    List <NoteInfo> remoteNotes = remoteRepo.list();
    
    List<String> uploadIDs = findUploadNotes(localNotes, remoteNotes);
    LOG.info("Notes with the following IDs will be uploaded");
    for (String id : uploadIDs) {
      LOG.info("ID : " + id);
    }
    saveToRemote(uploadIDs, localRepo, remoteRepo);
    LOG.info("Sync end");
  }

  private void saveToRemote(List<String> ids, VFSNotebookRepo localRepo,
                            NotebookRepo remoteRepo) throws IOException {
    for (String id : ids) {
      remoteRepo.save(localRepo.get(id));
    }
  
  }
  private List<String> findUploadNotes(List <NoteInfo> localNotes, List <NoteInfo> remoteNotes) {
    List <String> ids = new ArrayList<String>();
    NoteInfo rnote;
    for (NoteInfo lnote : localNotes) {
      rnote = containsID(remoteNotes, lnote.getId());
      if ( rnote != null) {
        /* note may exist, but outdated
         * currently using file modification timestamps, other option: hash
         */
        //LOG.info("Local note modification time is: " + lnote.getModTime());
        //LOG.info("Remote note modification time is: " + rnote.getModTime());
        if (lnote.getModTime() > rnote.getModTime()) {
          ids.add(lnote.getId());
        }
      } else {
        /* this note exists in local fs, and doesnt exist in remote fs
         * need to upload it 
         * (another scenario is that it was deleted from remote)*/
        ids.add(lnote.getId());
      }
    }
    
    return ids;
  }

  private NoteInfo containsID(List <NoteInfo> notes, String id) { 
    for (NoteInfo note : notes) {
      if (note.getId().equals(id)) {
        return note;
      }
    }
    return null;
  }
  
  private void printNoteInfo(NoteInfo note) {
    LOG.info("Note info of notebook with name : " + note.getName());
    LOG.info("ID : " + note.getId());
    Map<String, Object> configs = note.getConfig();
    for (Map.Entry<String, Object> entry : configs.entrySet()) {
      LOG.info("Config Key = " + entry.getKey() + "  , Value = " + 
        entry.getValue().toString() + "of class " + entry.getClass());
    }
  }

}
