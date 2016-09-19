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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notebook repository sync with remote storage
 */
public class NotebookRepoSync implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoSync.class);
  private static final int maxRepoNum = 2;
  private static final String pushKey = "pushNoteIDs";
  private static final String pullKey = "pullNoteIDs";
  private static final String delDstKey = "delDstNoteIDs";

  private static ZeppelinConfiguration config;
  private static final String defaultStorage = "org.apache.zeppelin.notebook.repo.VFSNotebookRepo";

  private List<NotebookRepo> repos = new ArrayList<NotebookRepo>();
  private final boolean oneWaySync;

  /**
   * @param noteIndex
   * @param (conf)
   * @throws - Exception
   */
  @SuppressWarnings("static-access")
  public NotebookRepoSync(ZeppelinConfiguration conf) {
    config = conf;
    oneWaySync = conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC);
    String allStorageClassNames = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE).trim();
    if (allStorageClassNames.isEmpty()) {
      allStorageClassNames = defaultStorage;
      LOG.warn("Empty ZEPPELIN_NOTEBOOK_STORAGE conf parameter, using default {}", defaultStorage);
    }
    String[] storageClassNames = allStorageClassNames.split(",");
    if (storageClassNames.length > getMaxRepoNum()) {
      LOG.warn("Unsupported number {} of storage classes in ZEPPELIN_NOTEBOOK_STORAGE : {}\n" +
        "first {} will be used", storageClassNames.length, allStorageClassNames, getMaxRepoNum());
    }

    for (int i = 0; i < Math.min(storageClassNames.length, getMaxRepoNum()); i++) {
      @SuppressWarnings("static-access")
      Class<?> notebookStorageClass;
      try {
        notebookStorageClass = getClass().forName(storageClassNames[i].trim());
        Constructor<?> constructor = notebookStorageClass.getConstructor(
                  ZeppelinConfiguration.class);
        repos.add((NotebookRepo) constructor.newInstance(conf));
      } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
          InstantiationException | IllegalAccessException | IllegalArgumentException |
          InvocationTargetException e) {
        LOG.warn("Failed to initialize {} notebook storage class", storageClassNames[i], e);
      }
    }
    // couldn't initialize any storage, use default
    if (getRepoCount() == 0) {
      LOG.info("No storages could be initialized, using default {} storage", defaultStorage);
      initializeDefaultStorage(conf);
    }
    if (getRepoCount() > 1) {
      try {
        AuthenticationInfo subject = new AuthenticationInfo("anonymous");
        sync(0, 1, subject);
      } catch (IOException e) {
        LOG.warn("Failed to sync with secondary storage on start {}", e);
      }
    }
  }

  @SuppressWarnings("static-access")
  private void initializeDefaultStorage(ZeppelinConfiguration conf) {
    Class<?> notebookStorageClass;
    try {
      notebookStorageClass = getClass().forName(defaultStorage);
      Constructor<?> constructor = notebookStorageClass.getConstructor(
                ZeppelinConfiguration.class);
      repos.add((NotebookRepo) constructor.newInstance(conf));
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
        InstantiationException | IllegalAccessException | IllegalArgumentException |
        InvocationTargetException e) {
      LOG.warn("Failed to initialize {} notebook storage class {}", defaultStorage, e);
    }
  }

  /**
   *  Lists Notebooks from the first repository
   */
  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    return getRepo(0).list(subject);
  }

  /* list from specific repo (for tests) */
  List<NoteInfo> list(int repoIndex, AuthenticationInfo subject) throws IOException {
    return getRepo(repoIndex).list(subject);
  }

  /**
   *  Returns from Notebook from the first repository
   */
  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    return getRepo(0).get(noteId, subject);
  }

  /* get note from specific repo (for tests) */
  Note get(int repoIndex, String noteId, AuthenticationInfo subject) throws IOException {
    return getRepo(repoIndex).get(noteId, subject);
  }

  /**
   *  Saves to all repositories
   */
  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    getRepo(0).save(note, subject);
    if (getRepoCount() > 1) {
      try {
        getRepo(1).save(note, subject);
      }
      catch (IOException e) {
        LOG.info(e.getMessage() + ": Failed to write to secondary storage");
      }
    }
  }

  /* save note to specific repo (for tests) */
  void save(int repoIndex, Note note, AuthenticationInfo subject) throws IOException {
    getRepo(repoIndex).save(note, subject);
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    for (NotebookRepo repo : repos) {
      repo.remove(noteId, subject);
    }
    /* TODO(khalid): handle case when removing from secondary storage fails */
  }

  /**
   * Copies new/updated notes from source to destination storage
   *
   * @throws IOException
   */
  void sync(int sourceRepoIndex, int destRepoIndex, AuthenticationInfo subject) throws IOException {
    LOG.info("Sync started");
    NotebookAuthorization auth = NotebookAuthorization.getInstance();
    NotebookRepo srcRepo = getRepo(sourceRepoIndex);
    NotebookRepo dstRepo = getRepo(destRepoIndex);
    List <NoteInfo> allSrcNotes = srcRepo.list(subject);
    List <NoteInfo> srcNotes = auth.filterByUser(allSrcNotes, subject);
    List <NoteInfo> dstNotes = dstRepo.list(subject);

    Map<String, List<String>> noteIDs = notesCheckDiff(srcNotes, srcRepo, dstNotes, dstRepo);
    List<String> pushNoteIDs = noteIDs.get(pushKey);
    List<String> pullNoteIDs = noteIDs.get(pullKey);
    List<String> delDstNoteIDs = noteIDs.get(delDstKey);

    if (!pushNoteIDs.isEmpty()) {
      LOG.info("Notes with the following IDs will be pushed");
      for (String id : pushNoteIDs) {
        LOG.info("ID : " + id);
      }
      pushNotes(subject, pushNoteIDs, srcRepo, dstRepo);
    } else {
      LOG.info("Nothing to push");
    }

    if (!pullNoteIDs.isEmpty()) {
      LOG.info("Notes with the following IDs will be pulled");
      for (String id : pullNoteIDs) {
        LOG.info("ID : " + id);
      }
      pushNotes(subject, pullNoteIDs, dstRepo, srcRepo);
    } else {
      LOG.info("Nothing to pull");
    }

    if (!delDstNoteIDs.isEmpty()) {
      LOG.info("Notes with the following IDs will be deleted from dest");
      for (String id : delDstNoteIDs) {
        LOG.info("ID : " + id);
      }
      deleteNotes(subject, delDstNoteIDs, dstRepo);
    } else {
      LOG.info("Nothing to delete from dest");
    }

    LOG.info("Sync ended");
  }

  public void sync(AuthenticationInfo subject) throws IOException {
    sync(0, 1, subject);
  }

  private void pushNotes(AuthenticationInfo subject, List<String> ids, NotebookRepo localRepo,
      NotebookRepo remoteRepo) throws IOException {
    for (String id : ids) {
      remoteRepo.save(localRepo.get(id, subject), subject);
    }
  }

  private void deleteNotes(AuthenticationInfo subject, List<String> ids, NotebookRepo repo)
      throws IOException {
    for (String id : ids) {
      repo.remove(id, subject);
    }
  }

  public int getRepoCount() {
    return repos.size();
  }

  int getMaxRepoNum() {
    return maxRepoNum;
  }

  NotebookRepo getRepo(int repoIndex) throws IOException {
    if (repoIndex < 0 || repoIndex >= getRepoCount()) {
      throw new IOException("Requested storage index " + repoIndex
          + " isn't initialized," + " repository count is " + getRepoCount());
    }
    return repos.get(repoIndex);
  }

  private Map<String, List<String>> notesCheckDiff(List<NoteInfo> sourceNotes,
      NotebookRepo sourceRepo, List<NoteInfo> destNotes, NotebookRepo destRepo)
      throws IOException {
    List <String> pushIDs = new ArrayList<String>();
    List <String> pullIDs = new ArrayList<String>();
    List <String> delDstIDs = new ArrayList<String>();

    NoteInfo dnote;
    Date sdate, ddate;
    for (NoteInfo snote : sourceNotes) {
      dnote = containsID(destNotes, snote.getId());
      if (dnote != null) {
        /* note exists in source and destination storage systems */
        sdate = lastModificationDate(sourceRepo.get(snote.getId(), null));
        ddate = lastModificationDate(destRepo.get(dnote.getId(), null));

        if (sdate.compareTo(ddate) != 0) {
          if (sdate.after(ddate) || oneWaySync) {
            /* if source contains more up to date note - push
             * if oneWaySync is enabled, always push no matter who's newer */
            pushIDs.add(snote.getId());
            LOG.info("Modified note is added to push list : " + sdate);
          } else {
            /* destination contains more up to date note - pull */
            LOG.info("Modified note is added to pull list : " + ddate);
            pullIDs.add(snote.getId());
          }
        }
      } else {
        /* note exists in source storage, and absent in destination
         * view source as up to date - push
         * (another scenario : note was deleted from destination - not considered)*/
        pushIDs.add(snote.getId());
      }
    }

    for (NoteInfo note : destNotes) {
      dnote = containsID(sourceNotes, note.getId());
      if (dnote == null) {
        /* note exists in destination storage, and absent in source */
        if (oneWaySync) {
          /* if oneWaySync is enabled, delete the note from destination */
          LOG.info("Extraneous note is added to delete dest list : " + note.getId());
          delDstIDs.add(note.getId());
        } else {
          /* if oneWaySync is disabled, pull the note from destination */
          LOG.info("Missing note is added to pull list : " + note.getId());
          pullIDs.add(note.getId());
        }
      }
    }

    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put(pushKey, pushIDs);
    map.put(pullKey, pullIDs);
    map.put(delDstKey, delDstIDs);
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

  @Override
  public void close() {
    LOG.info("Closing all notebook storages");
    for (NotebookRepo repo: repos) {
      repo.close();
    }
  }

  //checkpoint to all available storages
  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    int repoCount = getRepoCount();
    int repoBound = Math.min(repoCount, getMaxRepoNum());
    int errorCount = 0;
    String errorMessage = "";
    List<Revision> allRepoCheckpoints = new ArrayList<Revision>();
    Revision rev = null;
    for (int i = 0; i < repoBound; i++) {
      try {
        allRepoCheckpoints.add(getRepo(i).checkpoint(noteId, checkpointMsg, subject));
      } catch (IOException e) {
        LOG.warn("Couldn't checkpoint in {} storage with index {} for note {}",
          getRepo(i).getClass().toString(), i, noteId);
        errorMessage += "Error on storage class " + getRepo(i).getClass().toString() +
          " with index " + i + " : " + e.getMessage() + "\n";
        errorCount++;
      }
    }
    // throw exception if failed to commit for all initialized repos
    if (errorCount == repoBound) {
      throw new IOException(errorMessage);
    }
    if (allRepoCheckpoints.size() > 0) {
      rev = allRepoCheckpoints.get(0);
      // if failed to checkpoint on first storage, then return result on second
      if (allRepoCheckpoints.size() > 1 && rev == null) {
        rev = allRepoCheckpoints.get(1);
      }
    }
    return rev;
  }

  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) {
    Note revisionNote = null;
    try {
      revisionNote = getRepo(0).get(noteId, revId, subject);
    } catch (IOException e) {
      LOG.error("Failed to get revision {} of note {}", revId, noteId, e);
    }
    return revisionNote;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    List<Revision> revisions = Collections.emptyList();
    try {
      revisions = getRepo(0).revisionHistory(noteId, subject);
    } catch (IOException e) {
      LOG.error("Failed to list revision history", e);
    }
    return revisions;
  }
}
