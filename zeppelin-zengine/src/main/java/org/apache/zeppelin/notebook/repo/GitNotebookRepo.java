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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * NotebookRepo that hosts all the notebook FS in a single Git repo
 *
 * This impl intended to be simple and straightforward:
 *   - does not handle branches
 *   - only basic local git file repo, no remote Github push\pull. GitHub integration is
 *   implemented in @see {@link org.apache.zeppelin.notebook.repo.GitNotebookRepo}
 *
 *   TODO(bzz): add default .gitignore
 */
public class GitNotebookRepo extends VFSNotebookRepo implements NotebookRepoWithVersionControl {
  private static final Logger LOGGER = LoggerFactory.getLogger(GitNotebookRepo.class);

  private Git git;
  protected String gitRepoDir;

  public GitNotebookRepo() {
    super();
  }

  @VisibleForTesting
  public GitNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this();
    init(conf);
  }

  @Override
  public void init(ZeppelinConfiguration conf) throws IOException {
    //TODO(zjffdu), it is weird that I can not call super.init directly here, as it would cause
    //AbstractMethodError
    this.conf = conf;
    setNotebookDirectory(conf.getNotebookDir());
    setGitRepoDir(conf.getGitRepoDir());

    LOGGER.info("Opening a git repo at '{}'", this.gitRepoDir);
    Repository localRepo = new FileRepository(Joiner.on(File.separator)
        .join(this.gitRepoDir, ".git"));
    if (!localRepo.getDirectory().exists()) {
      LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
      localRepo.create();
    }
    git = new Git(localRepo);
  }

  @Override
  public void move(String noteId,
                   String notePath,
                   String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    super.move(noteId, notePath, newNotePath, subject);
    String noteFileName = buildNoteFileNameForGitRepo(noteId, notePath);
    String newNoteFileName = buildNoteFileNameForGitRepo(noteId, newNotePath);
    git.rm().addFilepattern(noteFileName);
    git.add().addFilepattern(newNoteFileName);
    try {
      git.commit().setMessage("Move note " + noteId + " from " + noteFileName + " to " +
          newNoteFileName).call();
    } catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void move(String folderPath, String newFolderPath,
                   AuthenticationInfo subject) throws IOException {
    super.move(folderPath, newFolderPath, subject);
    git.rm().addFilepattern(folderPath.substring(1));
    git.add().addFilepattern(newFolderPath.substring(1));
    try {
      git.commit().setMessage("Move folder " + folderPath + " to " + newFolderPath).call();
    } catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  /* implemented as git add+commit
   * @param noteId is the noteId
   * @param noteName name of the note
   * @param commitMessage is a commit message (checkpoint message)
   * (non-Javadoc)
   * @see org.apache.zeppelin.notebook.repo.VFSNotebookRepo#checkpoint(String, String)
   */
  @Override
  public Revision checkpoint(String noteId,
                             String notePath,
                             String commitMessage,
                             AuthenticationInfo subject) throws IOException {
    String noteFileName = buildNoteFileNameForGitRepo(noteId, notePath);
    Revision revision = Revision.EMPTY;
    try {
      List<DiffEntry> gitDiff = git.diff().call();
      boolean modified = gitDiff.parallelStream().anyMatch(diffEntry -> diffEntry.getNewPath().equals(noteFileName));
      if (modified) {
        LOGGER.debug("Changes found for pattern '{}': {}", noteFileName, gitDiff);
        DirCache added = git.add().addFilepattern(noteFileName).call();
        LOGGER.debug("{} changes are about to be commited", added.getEntryCount());
        RevCommit commit = git.commit().setMessage(commitMessage).call();
        revision = new Revision(commit.getName(), commit.getShortMessage(), commit.getCommitTime());
      } else {
        LOGGER.debug("No changes found {}", noteFileName);
      }
    } catch (GitAPIException e) {
      LOGGER.error("Failed to add+commit {} to Git", noteFileName, e);
    }
    return revision;
  }

  /**
   * the idea is to:
   * 1. stash current changes
   * 2. remember head commit and checkout to the desired revision
   * 3. get note and checkout back to the head
   * 4. apply stash on top and remove it
   */
  @Override
  public synchronized Note get(String noteId,
                               String notePath,
                               String revId,
                               AuthenticationInfo subject) throws IOException {
    Note note = null;
    RevCommit stash = null;
    String noteFileName = buildNoteFileNameForGitRepo(noteId, notePath);
    try {
      List<DiffEntry> gitDiff = git.diff().setPathFilter(PathFilter.create(noteFileName)).call();
      boolean modified = !gitDiff.isEmpty();
      if (modified) {
        // stash changes
        stash = git.stashCreate().call();
        Collection<RevCommit> stashes = git.stashList().call();
        LOGGER.debug("Created stash : {}, stash size : {}", stash, stashes.size());
      }
      ObjectId head = git.getRepository().resolve(Constants.HEAD);
      // checkout to target revision
      git.checkout().setStartPoint(revId).addPath(noteFileName).call();
      // get the note
      note = super.get(noteId, notePath, subject);
      // checkout back to head
      git.checkout().setStartPoint(head.getName()).addPath(noteFileName).call();
      if (modified && stash != null) {
        // unstash changes
        ObjectId applied = git.stashApply().setStashRef(stash.getName()).call();
        ObjectId dropped = git.stashDrop().setStashRef(0).call();
        Collection<RevCommit> stashes = git.stashList().call();
        LOGGER.debug("Stash applied as : {}, and dropped : {}, stash size: {}", applied, dropped,
            stashes.size());
      }
    } catch (GitAPIException e) {
      LOGGER.error("Failed to return note from revision \"{}\"", revId, e);
    }
    return note;
  }

  @Override
  public List<Revision> revisionHistory(String noteId,
                                        String notePath,
                                        AuthenticationInfo subject) throws IOException {
    List<Revision> history = Lists.newArrayList();
    String noteFileName = buildNoteFileNameForGitRepo(noteId, notePath);
    LOGGER.debug("Listing history for {}:", noteFileName);
    try {
      Iterable<RevCommit> logs = git.log().addPath(noteFileName).call();
      for (RevCommit log: logs) {
        history.add(new Revision(log.getName(), log.getShortMessage(), log.getCommitTime()));
        LOGGER.debug(" - ({},{},{})", log.getName(), log.getCommitTime(), log.getFullMessage());
      }
    } catch (NoHeadException e) {
      //when no initial commit exists
      LOGGER.warn("No Head found for {}, {}", noteFileName, e.getMessage());
    } catch (GitAPIException e) {
      LOGGER.error("Failed to get logs for {}", noteFileName, e);
    }
    return history;
  }

  @Override
  public Note setNoteRevision(String noteId, String notePath, String revId,
                              AuthenticationInfo subject)
      throws IOException {
    Note revisionNote = get(noteId, notePath, revId, subject);
    if (revisionNote != null) {
      save(revisionNote, subject);
    }
    return revisionNote;
  }
  
  @Override
  public void close() {
    git.getRepository().close();
  }

  //DI replacements for Tests
  protected Git getGit() {
    return git;
  }

  void setGit(Git git) {
    this.git = git;
  }

  protected void setGitRepoDir(String gitRepoDir) {
    if (StringUtils.isEmpty(gitRepoDir)) {
      this.gitRepoDir = rootNotebookFolder;
    } else {
      this.gitRepoDir = gitRepoDir;
    }
    if (!isChild(rootNotebookFolder, this.gitRepoDir)) {
      throw new IllegalArgumentException("Notebook directory (" + rootNotebookFolder + ")" +
              " must be nested within Git Repo (" + gitRepoDir + ")");
    }
  }

  private Boolean isChild(String childText, String parentText) {
    Path child = Paths.get(childText.replaceAll("^/*", "")).toAbsolutePath();
    Path parent = Paths.get(parentText.replaceAll("^/*", "")).toAbsolutePath();
    return child.startsWith(parent);
  }

  protected String buildNoteFileNameForGitRepo(String noteId, String notePath) throws IOException {
    Path child = Paths.get(rootNotebookFolder.replaceAll("^/*", "")).toAbsolutePath();
    Path parent = Paths.get(gitRepoDir.replaceAll("^/*", "")).toAbsolutePath();
    String notepathPrefix = "/" + parent.relativize(child).toString().replace("\\","/");
    return buildNoteFileName(noteId, notepathPrefix + notePath);
  }

}

