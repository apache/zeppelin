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

import com.google.common.collect.Lists;
import javax.inject.Inject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.OldNoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.zeppelinhub.security.Authentication;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.Util;
import org.glassfish.hk2.api.Immediate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Notebook repository sync with remote storage
 */
@Immediate
public class NotebookRepoSync implements NotebookRepoWithVersionControl {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookRepoSync.class);
  private static final int maxRepoNum = 2;
  private static final String pushKey = "pushNoteIds";
  private static final String pullKey = "pullNoteIds";
  private static final String delDstKey = "delDstNoteIds";

  private static final String DEFAULT_STORAGE = "org.apache.zeppelin.notebook.repo.GitNotebookRepo";

  private List<NotebookRepo> repos = new ArrayList<>();
  private boolean oneWaySync;

  /**
   * @param conf
   */
  @SuppressWarnings("static-access")
  @Inject
  public NotebookRepoSync(ZeppelinConfiguration conf) throws IOException {
    init(conf);
  }

  public void init(ZeppelinConfiguration conf) throws IOException {
    oneWaySync = conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC);
    String allStorageClassNames = conf.getNotebookStorageClass().trim();
    if (allStorageClassNames.isEmpty()) {
      allStorageClassNames = DEFAULT_STORAGE;
      LOGGER.warn("Empty ZEPPELIN_NOTEBOOK_STORAGE conf parameter, using default {}",
              DEFAULT_STORAGE);
    }
    String[] storageClassNames = allStorageClassNames.split(",");
    if (storageClassNames.length > getMaxRepoNum()) {
      LOGGER.warn("Unsupported number {} of storage classes in ZEPPELIN_NOTEBOOK_STORAGE : {}\n" +
          "first {} will be used", storageClassNames.length, allStorageClassNames, getMaxRepoNum());
    }

    // init the underlying NotebookRepo
    for (int i = 0; i < Math.min(storageClassNames.length, getMaxRepoNum()); i++) {
      NotebookRepo notebookRepo = PluginManager.get().loadNotebookRepo(storageClassNames[i].trim());
      if (notebookRepo != null) {
        notebookRepo.init(conf);
        repos.add(notebookRepo);
      }
    }

    // couldn't initialize any storage, use default
    if (getRepoCount() == 0) {
      LOGGER.info("No storage could be initialized, using default {} storage", DEFAULT_STORAGE);
      NotebookRepo defaultNotebookRepo = PluginManager.get().loadNotebookRepo(DEFAULT_STORAGE);
      defaultNotebookRepo.init(conf);
      repos.add(defaultNotebookRepo);
    }

    // sync for anonymous mode on start
    if (getRepoCount() > 1 && conf.getBoolean(ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED)) {
      try {
        sync(AuthenticationInfo.ANONYMOUS);
      } catch (IOException e) {
        LOGGER.error("Couldn't sync anonymous mode on start ", e);
      }
    }
  }

  // Zeppelin change its note file name structure in 0.9.0, this is called when upgrading
  // from 0.9.0 before to 0.9.0 after
  public void convertNoteFiles(ZeppelinConfiguration conf, boolean deleteOld) throws IOException {
    // convert old note file (noteId/note.json) to new note file (note_name_note_id.zpln)
    for (int i = 0; i < repos.size(); ++i) {
      NotebookRepo newNotebookRepo = repos.get(i);
      OldNotebookRepo oldNotebookRepo =
              PluginManager.get().loadOldNotebookRepo(newNotebookRepo.getClass().getCanonicalName());
      oldNotebookRepo.init(conf);
      List<OldNoteInfo> oldNotesInfo = oldNotebookRepo.list(AuthenticationInfo.ANONYMOUS);
      LOGGER.info("Convert old note file to new style, note count: " + oldNotesInfo.size());
      LOGGER.info("Delete old note: " + deleteOld);
      for (OldNoteInfo oldNoteInfo : oldNotesInfo) {
        Note note = oldNotebookRepo.get(oldNoteInfo.getId(), AuthenticationInfo.ANONYMOUS);
        note.setPath(note.getName());
        note.setVersion(Util.getVersion());
        newNotebookRepo.save(note, AuthenticationInfo.ANONYMOUS);
        if (newNotebookRepo instanceof NotebookRepoWithVersionControl) {
          ((NotebookRepoWithVersionControl) newNotebookRepo).checkpoint(
                  note.getId(),
                  note.getPath(),
                  "Upgrade note '" + note.getName() + "' to " + Util.getVersion(),
                  AuthenticationInfo.ANONYMOUS);
        }
        if (deleteOld) {
          oldNotebookRepo.remove(note.getId(), AuthenticationInfo.ANONYMOUS);
          LOGGER.info("Remote old note: " + note.getId());
          // TODO(zjffdu) no commit when deleting note, This is an issue of
          // NotebookRepoWithVersionControl
          /**
          if (oldNotebookRepo instanceof NotebookRepoWithVersionControl) {
            ((NotebookRepoWithVersionControl) oldNotebookRepo).checkpoint(
                    note.getId(),
                    note.getName(),
                    "Delete note '" + note.getName() + "' during note upgrade",
                    AuthenticationInfo.ANONYMOUS);
          }
           **/
        }
      }
    }
  }

  public void mergeAuthorizationInfo() throws IOException {
    LOGGER.info("Merge AuthorizationInfo into note file");
    NotebookAuthorization notebookAuthorization = NotebookAuthorization.getInstance();
    for (int i = 0; i < repos.size(); ++i) {
      NotebookRepo notebookRepo = repos.get(i);
      Map<String, NoteInfo> notesInfo = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
      for (NoteInfo noteInfo : notesInfo.values()) {
        Note note = notebookRepo.get(noteInfo.getId(), noteInfo.getPath(), AuthenticationInfo.ANONYMOUS);
        note.setOwners(notebookAuthorization.getOwners(noteInfo.getId()));
        note.setRunners(notebookAuthorization.getRunners(noteInfo.getId()));
        note.setReaders(notebookAuthorization.getReaders(noteInfo.getId()));
        note.setWriters(notebookAuthorization.getWriters(noteInfo.getId()));
        notebookRepo.save(note, AuthenticationInfo.ANONYMOUS);
      }
    }
  }

  public List<NotebookRepoWithSettings> getNotebookRepos(AuthenticationInfo subject) {
    List<NotebookRepoWithSettings> reposSetting = Lists.newArrayList();

    NotebookRepoWithSettings repoWithSettings;
    for (NotebookRepo repo : repos) {
      repoWithSettings = NotebookRepoWithSettings
                           .builder(repo.getClass().getSimpleName())
                           .className(repo.getClass().getName())
                           .settings(repo.getSettings(subject))
                           .build();
      reposSetting.add(repoWithSettings);
    }

    return reposSetting;
  }

  public NotebookRepoWithSettings updateNotebookRepo(String name, Map<String, String> settings,
                                                     AuthenticationInfo subject) {
    NotebookRepoWithSettings updatedSettings = NotebookRepoWithSettings.EMPTY;
    for (NotebookRepo repo : repos) {
      if (repo.getClass().getName().equals(name)) {
        repo.updateSettings(settings, subject);
        updatedSettings = NotebookRepoWithSettings
                            .builder(repo.getClass().getSimpleName())
                            .className(repo.getClass().getName())
                            .settings(repo.getSettings(subject))
                            .build();
        break;
      }
    }
    return updatedSettings;
  }

  /**
   *  Lists Notebooks from the first repository
   */
  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    return getRepo(0).list(subject);
  }

  /* list from specific repo (for tests) */
  List<NoteInfo> list(int repoIndex, AuthenticationInfo subject) throws IOException {
    return new ArrayList<>(getRepo(repoIndex).list(subject).values());
  }

  /**
   *  Returns from Notebook from the first repository
   */
  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    return getRepo(0).get(noteId, notePath, subject);
  }

  /* get note from specific repo (for tests) */
  Note get(int repoIndex, String noteId, String noteName, AuthenticationInfo subject) throws IOException {
    return getRepo(repoIndex).get(noteId, noteName, subject);
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
        LOGGER.info(e.getMessage() + ": Failed to write to secondary storage");
      }
    }
  }

  /* save note to specific repo (for tests) */
  void save(int repoIndex, Note note, AuthenticationInfo subject) throws IOException {
    getRepo(repoIndex).save(note, subject);
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    getRepo(0).move(noteId, notePath, newNotePath, subject);
    if (getRepoCount() > 1) {
      try {
        getRepo(1).move(noteId, notePath, newNotePath, subject);
      }
      catch (IOException e) {
        LOGGER.info(e.getMessage() + ": Failed to write to secondary storage");
      }
    }
  }

  @Override
  public void move(String folderPath, String newFolderPath,
                   AuthenticationInfo subject) throws IOException {
    for (NotebookRepo repo : repos) {
      repo.move(folderPath, newFolderPath, subject);
    }
  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    for (NotebookRepo repo : repos) {
      repo.remove(noteId, notePath, subject);
    }
    /* TODO(khalid): handle case when removing from secondary storage fails */
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    for (NotebookRepo repo : repos) {
      repo.remove(folderPath, subject);
    }
  }

  void remove(int repoIndex, String noteId, String noteName, AuthenticationInfo subject) throws IOException {
    getRepo(repoIndex).remove(noteId, noteName, subject);
  }

  /**
   * Copies new/updated notes from source to destination storage
   *
   * @throws IOException
   */
  void sync(int sourceRepoIndex, int destRepoIndex, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Sync started");
    NotebookRepo srcRepo = getRepo(sourceRepoIndex);
    NotebookRepo dstRepo = getRepo(destRepoIndex);
    List<NoteInfo> srcNotes = new ArrayList<>(srcRepo.list(subject).values());
    List<NoteInfo> dstNotes = new ArrayList<>(dstRepo.list(subject).values());

    Map<String, List<NoteInfo>> noteIds = notesCheckDiff(srcNotes, srcRepo, dstNotes, dstRepo,
        subject);
    List<NoteInfo> pushNoteIds = noteIds.get(pushKey);
    List<NoteInfo> pullNoteIds = noteIds.get(pullKey);
    List<NoteInfo> delDstNoteIds = noteIds.get(delDstKey);

    if (!pushNoteIds.isEmpty()) {
      LOGGER.info("The following notes will be pushed");
      for (NoteInfo noteInfo : pushNoteIds) {
        LOGGER.info("Note : " + noteIds);
      }
      pushNotes(subject, pushNoteIds, srcRepo, dstRepo);
    } else {
      LOGGER.info("Nothing to push");
    }

    if (!pullNoteIds.isEmpty()) {
      LOGGER.info("The following notes will be pulled");
      for (NoteInfo noteInfo : pullNoteIds) {
        LOGGER.info("Note : " + noteInfo);
      }
      pushNotes(subject, pullNoteIds, dstRepo, srcRepo);
    } else {
      LOGGER.info("Nothing to pull");
    }

    if (!delDstNoteIds.isEmpty()) {
      LOGGER.info("The following notes will be deleted from dest");
      for (NoteInfo noteInfo : delDstNoteIds) {
        LOGGER.info("Note : " + noteIds);
      }
      deleteNotes(subject, delDstNoteIds, dstRepo);
    } else {
      LOGGER.info("Nothing to delete from dest");
    }

    LOGGER.info("Sync ended");
  }

  public void sync(AuthenticationInfo subject) throws IOException {
    sync(0, 1, subject);
  }

  private void pushNotes(AuthenticationInfo subject, List<NoteInfo> notesInfo, NotebookRepo localRepo,
      NotebookRepo remoteRepo) {
    for (NoteInfo noteInfo : notesInfo) {
      try {
        remoteRepo.save(localRepo.get(noteInfo.getId(), noteInfo.getPath(), subject), subject);
      } catch (IOException e) {
        LOGGER.error("Failed to push note to storage, moving onto next one", e);
      }
    }
  }

  private void deleteNotes(AuthenticationInfo subject, List<NoteInfo> noteInfos, NotebookRepo repo)
      throws IOException {
    for (NoteInfo noteInfo : noteInfos) {
      repo.remove(noteInfo.getId(), noteInfo.getPath(), subject);
    }
  }

  public int getRepoCount() {
    return repos.size();
  }

  int getMaxRepoNum() {
    return maxRepoNum;
  }

  public NotebookRepo getRepo(int repoIndex) throws IOException {
    if (repoIndex < 0 || repoIndex >= getRepoCount()) {
      throw new IOException("Requested storage index " + repoIndex
          + " isn't initialized," + " repository count is " + getRepoCount());
    }
    return repos.get(repoIndex);
  }

  private Map<String, List<NoteInfo>> notesCheckDiff(List<NoteInfo> sourceNotes,
      NotebookRepo sourceRepo, List<NoteInfo> destNotes, NotebookRepo destRepo,
      AuthenticationInfo subject) {
    List<NoteInfo> pushIDs = new ArrayList<>();
    List<NoteInfo> pullIDs = new ArrayList<>();
    List<NoteInfo> delDstIDs = new ArrayList<>();

    NoteInfo dnote;
    Date sdate, ddate;
    for (NoteInfo snote : sourceNotes) {
      dnote = containsID(destNotes, snote.getId());
      if (dnote != null) {
        try {
          /* note exists in source and destination storage systems */
          sdate = lastModificationDate(sourceRepo.get(snote.getId(), snote.getPath(), subject));
          ddate = lastModificationDate(destRepo.get(dnote.getId(), dnote.getPath(), subject));
        } catch (IOException e) {
          LOGGER.error("Cannot access previously listed note {} from storage ", dnote.getId(), e);
          continue;
        }

        if (sdate.compareTo(ddate) != 0) {
          if (sdate.after(ddate) || oneWaySync) {
            /* if source contains more up to date note - push
             * if oneWaySync is enabled, always push no matter who's newer */
            pushIDs.add(snote);
            LOGGER.info("Modified note is added to push list : " + sdate);
          } else {
            /* destination contains more up to date note - pull */
            LOGGER.info("Modified note is added to pull list : " + ddate);
            pullIDs.add(snote);
          }
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
        /* note exists in destination storage, and absent in source */
        if (oneWaySync) {
          /* if oneWaySync is enabled, delete the note from destination */
          LOGGER.info("Extraneous note is added to delete dest list : " + note.getId());
          delDstIDs.add(note);
        } else {
          /* if oneWaySync is disabled, pull the note from destination */
          LOGGER.info("Missing note is added to pull list : " + note.getId());
          pullIDs.add(note);
        }
      }
    }

    Map<String, List<NoteInfo>> map = new HashMap<>();
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
    LOGGER.info("Closing all notebook storages");
    for (NotebookRepo repo: repos) {
      repo.close();
    }
  }

  public Boolean isRevisionSupportedInDefaultRepo() {
    return isRevisionSupportedInRepo(0);
  }

  public Boolean isRevisionSupportedInRepo(int repoIndex) {
    try {
      if (getRepo(repoIndex) instanceof NotebookRepoWithVersionControl) {
        return true;
      }
    } catch (IOException e) {
      LOGGER.error("Error getting default repo", e);
    }
    return false;
  }

  //checkpoint to all available storages
  @Override
  public Revision checkpoint(String noteId, String notePath, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    int repoCount = getRepoCount();
    int repoBound = Math.min(repoCount, getMaxRepoNum());
    int errorCount = 0;
    String errorMessage = "";
    List<Revision> allRepoCheckpoints = new ArrayList<>();
    Revision rev = null;
    for (int i = 0; i < repoBound; i++) {
      try {
        if (isRevisionSupportedInRepo(i)) {
          allRepoCheckpoints
              .add(((NotebookRepoWithVersionControl) getRepo(i))
                  .checkpoint(noteId, notePath, checkpointMsg, subject));
        }
      } catch (IOException e) {
        LOGGER.warn("Couldn't checkpoint in {} storage with index {} for note {}",
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
  public Note get(String noteId, String notePath, String revId, AuthenticationInfo subject) {
    Note revisionNote = null;
    try {
      if (isRevisionSupportedInDefaultRepo()) {
        revisionNote = ((NotebookRepoWithVersionControl) getRepo(0)).get(noteId, notePath,
            revId, subject);
      }
    } catch (IOException e) {
      LOGGER.error("Failed to get revision {} of note {}", revId, noteId, e);
    }
    return revisionNote;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, String notePath,
                                        AuthenticationInfo subject) {
    List<Revision> revisions = Collections.emptyList();
    try {
      if (isRevisionSupportedInDefaultRepo()) {
        revisions = ((NotebookRepoWithVersionControl) getRepo(0))
            .revisionHistory(noteId, notePath, subject);
      }
    } catch (IOException e) {
      LOGGER.error("Failed to list revision history", e);
    }
    return revisions;
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    List<NotebookRepoSettingsInfo> repoSettings = Collections.emptyList();
    try {
      repoSettings =  getRepo(0).getSettings(subject);
    } catch (IOException e) {
      LOGGER.error("Cannot get notebook repo settings", e);
    }
    return repoSettings;
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    try {
      getRepo(0).updateSettings(settings, subject);
    } catch (IOException e) {
      LOGGER.error("Cannot update notebook repo settings", e);
    }
  }

  @Override
  public Note setNoteRevision(String noteId, String notePath, String revId, AuthenticationInfo subject)
      throws IOException {
    int repoCount = getRepoCount();
    int repoBound = Math.min(repoCount, getMaxRepoNum());
    Note currentNote = null, revisionNote = null;
    for (int i = 0; i < repoBound; i++) {
      try {
        if (isRevisionSupportedInRepo(i)) {
          currentNote = ((NotebookRepoWithVersionControl) getRepo(i))
              .setNoteRevision(noteId, notePath, revId, subject);
        }
      } catch (IOException e) {
        // already logged
        currentNote = null;
      }
      // second condition assures that fist successful is returned
      if (currentNote != null && revisionNote == null) {
        revisionNote = currentNote;
      }
    }
    return revisionNote;
  }

}
