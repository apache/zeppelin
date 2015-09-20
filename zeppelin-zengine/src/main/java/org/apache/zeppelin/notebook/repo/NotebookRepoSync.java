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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notebook repository sync with remote storage
 */
public class NotebookRepoSync implements NotebookRepo{
  private List<NotebookRepo> repos = new ArrayList<NotebookRepo>();
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoSync.class);
  private static final int maxRepoNum = 2;
  private static final String pushKey = "pushNoteIDs";
  private static final String pullKey = "pullNoteIDs";

  /**
   * @param (conf)
   * @throws - Exception
   */
  public NotebookRepoSync(ZeppelinConfiguration conf) throws Exception {
    
    String allStorageClassNames = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE).trim();
    if (allStorageClassNames.isEmpty()) {
      throw new IOException("Empty ZEPPELIN_NOTEBOOK_STORAGE conf parameter");
    }
    String[] storageClassNames = allStorageClassNames.split(",");
    if (storageClassNames.length > getMaxRepoNum()) {
      throw new IOException("Unsupported number of storage classes (" + 
        storageClassNames.length + ") in ZEPPELIN_NOTEBOOK_STORAGE");
    }

    for (int i = 0; i < storageClassNames.length; i++) {
      Class<?> notebookStorageClass = getClass().forName(storageClassNames[i].trim());
      Constructor<?> constructor = notebookStorageClass.getConstructor(
                ZeppelinConfiguration.class);
      repos.add((NotebookRepo) constructor.newInstance(conf));
    }
    if (getRepoCount() > 1) {
      sync(0, 1);
    }
  }

  /* by default lists from first repository */
  public List<NoteInfo> list() throws IOException {
    return getRepo(0).list();
  }

  public List<NoteInfo> list(String owner) throws IOException {
    return getRepo(0).list(owner);
  }

    /* list from specific repo (for tests) */
  List<NoteInfo> list(int repoIndex) throws IOException {
    return getRepo(repoIndex).list();
  }

  List<NoteInfo> list(int repoIndex, String owner) throws IOException {
    return getRepo(repoIndex).list(owner);
  }

    /* by default returns from first repository */
  public Note get(String noteId, String owner) throws IOException {
    return getRepo(0).get(noteId, owner);
  }

  /* get note from specific repo (for tests) */
  Note get(int repoIndex, String noteId, String owner) throws IOException {
    return getRepo(repoIndex).get(noteId, owner);
  }
  
  /* by default saves to all repos */
  public void save(Note note) throws IOException {
    getRepo(0).save(note);
    if (getRepoCount() > 1) {
      try {
        getRepo(1).save(note);
      }
      catch (IOException e) {
        LOG.info(e.getMessage() + ": Failed to write to secondary storage");
      }
    }
  }

  /* save note to specific repo (for tests) */
  void save(int repoIndex, Note note) throws IOException {
    getRepo(repoIndex).save(note);
  }

  public void remove(String noteId, String owner) throws IOException {
    for (NotebookRepo repo : repos) {
      repo.remove(noteId, owner);
    }
    /* TODO(khalid): handle case when removing from secondary storage fails */
  }

  /**
   * copy new/updated notes from source to destination storage 
   * @throws IOException
   */
  public void sync(int sourceRepoIndex, int destRepoIndex) throws IOException {
    LOG.info("Sync started");
    NotebookRepo sourceRepo = getRepo(sourceRepoIndex);
    NotebookRepo destRepo = getRepo(destRepoIndex);
    List <NoteInfo> sourceNotes = sourceRepo.list();
    List <NoteInfo> destNotes = destRepo.list();
    
    Map<String, List<NoteInfo>> noteInfos = notesCheckDiff(sourceNotes,
                                                       sourceRepo,
                                                       destNotes,
                                                       destRepo);
    List<NoteInfo> pushNoteInfos = noteInfos.get(pushKey);
    List<NoteInfo> pullNoteInfos = noteInfos.get(pullKey);
    if (!pushNoteInfos.isEmpty()) {
      LOG.info("Notes with the following IDs will be pushed");
      for (NoteInfo noteInfo : pushNoteInfos) {
        LOG.info("ID : " + noteInfo.getId());
      }
      pushNotes(pushNoteInfos, sourceRepo, destRepo);
    } else {
      LOG.info("Nothing to push");
    }
    
    if (!pullNoteInfos.isEmpty()) {
      LOG.info("Notes with the following IDs will be pulled");
      for (NoteInfo noteInfo : pullNoteInfos) {
        LOG.info("ID : " + noteInfo.getId());
      }
      pushNotes(pullNoteInfos, destRepo, sourceRepo);
    } else {
      LOG.info("Nothing to pull");
    }
    
    LOG.info("Sync ended");
  }

  public void sync() throws IOException {
    sync(0, 1);
  }
  
  private void pushNotes(List<NoteInfo> noteInfos, NotebookRepo localRepo,
                            NotebookRepo remoteRepo) throws IOException {
    for (NoteInfo noteInfo: noteInfos) {
      remoteRepo.save(localRepo.get(noteInfo.getId(), noteInfo.getOwner()));
    }
  }

  int getRepoCount() {
    return repos.size();
  }
  
  int getMaxRepoNum() {
    return maxRepoNum;
  }

  private NotebookRepo getRepo(int repoIndex) throws IOException {
    if (repoIndex < 0 || repoIndex >= getRepoCount()) {
      throw new IOException("Storage repo index is out of range");
    }
    return repos.get(repoIndex);
  }
  
  private Map<String, List<NoteInfo>> notesCheckDiff(List <NoteInfo> sourceNotes,
                                                   NotebookRepo sourceRepo,
                                                   List <NoteInfo> destNotes,
                                                   NotebookRepo destRepo) throws IOException {
    List <NoteInfo> pushIDs = new ArrayList<NoteInfo>();
    List <NoteInfo> pullIDs = new ArrayList<NoteInfo>();
    
    NoteInfo dnote;
    Date sdate, ddate;
    for (NoteInfo snote : sourceNotes) {
      dnote = containsID(destNotes, snote.getId());
      if (dnote != null) {
        /* note exists in source and destination storage systems */
        sdate = lastModificationDate(sourceRepo.get(snote.getId(), snote.getOwner()));
        ddate = lastModificationDate(destRepo.get(dnote.getId(), snote.getOwner()));
        if (sdate.after(ddate)) {
          /* source contains more up to date note - push */
          pushIDs.add(snote);
          LOG.info("Modified note is added to push list : " + sdate);
        } else if (sdate.compareTo(ddate) != 0) {
          /* destination contains more up to date note - pull */
          LOG.info("Modified note is added to pull list : " + ddate);
          pullIDs.add(snote);
        }
      } else {
        /* note exists in source storage, and absent in destination
         * view source as up to date - push
         * (another scenario : note was deleted from destination - not considered)*/
        pushIDs.add(snote);
      }
    }
    
    for (NoteInfo note : destNotes) {
      dnote = containsID(sourceNotes, note.getId());
      if (dnote == null) {
        /* note exists in destination storage, and absent in source - pull*/
        pullIDs.add(note);
      }
    }
    
    Map<String, List<NoteInfo>> map = new HashMap<String, List<NoteInfo>>();
    map.put(pushKey, pushIDs);
    map.put(pullKey, pullIDs);
    return map;
  }

  private NoteInfo containsID(List <NoteInfo> notes, String id) { 
    for (NoteInfo note : notes) {
      if (note.getId().equals(id)) {
        return note;
      }
    }
    return null;
  }
  /**
   * checks latest modification date based on Paragraph fields
   * @return -Date
   */
  private Date lastModificationDate(Note note) {
    Date latest = new Date(0L);
    Date tempCreated, tempStarted, tempFinished;
    
    for (Paragraph paragraph : note.getParagraphs()) {
      tempCreated = paragraph.getDateCreated();
      tempStarted = paragraph.getDateStarted();
      tempFinished = paragraph.getDateFinished();

      if (tempCreated != null && tempCreated.after(latest)) {
        latest = tempCreated;
      }
      if (tempStarted != null && tempStarted.after(latest)) {
        latest = tempStarted;
      }
      if (tempFinished != null && tempFinished.after(latest)) {
        latest = tempFinished;
      }
    }
    return latest;
  }
  
  private void printParagraphs(Note note) {
    LOG.info("Note name :  " + note.getName());
    LOG.info("Note ID :  " + note.id());
    for (Paragraph p : note.getParagraphs()) {
      printParagraph(p);
    }
  }
  
  private void printParagraph(Paragraph paragraph) {
    LOG.info("Date created :  " + paragraph.getDateCreated());
    LOG.info("Date started :  " + paragraph.getDateStarted());
    LOG.info("Date finished :  " + paragraph.getDateFinished());
    LOG.info("Paragraph ID : " + paragraph.getId());
    LOG.info("Paragraph title : " + paragraph.getTitle());
  }
  
  private void printNoteInfos(List <NoteInfo> notes) {
    LOG.info("The following is a list of note infos");
    for (NoteInfo note : notes) {
      printNoteInfo(note);
    }
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
