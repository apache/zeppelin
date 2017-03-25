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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * NotebookRepo that hosts all the notebook FS in a single Git repo
 *
 * This impl intended to be simple and straightforward:
 *   - does not handle branches
 *   - only basic local git file repo, no remote Github push\pull yet
 *
 *   TODO(bzz): add default .gitignore
 */
public class GitNotebookRepo extends VFSNotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(GitNotebookRepo.class);

  private String localPath;
  private Git git;

  public GitNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    super(conf);
    localPath = getRootDir().getName().getPath();
    LOG.info("Opening a git repo at '{}'", localPath);
    Repository localRepo = new FileRepository(Joiner.on(File.separator).join(localPath, ".git"));
    if (!localRepo.getDirectory().exists()) {
      LOG.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
      localRepo.create();
    }
    git = new Git(localRepo);
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    super.save(note, subject);
  }

  /* implemented as git add+commit
   * @param pattern is the noteId
   * @param commitMessage is a commit message (checkpoint message)
   * (non-Javadoc)
   * @see org.apache.zeppelin.notebook.repo.VFSNotebookRepo#checkpoint(String, String)
   */
  @Override
  public Revision checkpoint(String pattern, String commitMessage, AuthenticationInfo subject) {
    Revision revision = Revision.EMPTY;
    try {
      List<DiffEntry> gitDiff = git.diff().call();
      if (!gitDiff.isEmpty()) {
        LOG.debug("Changes found for pattern '{}': {}", pattern, gitDiff);
        DirCache added = git.add().addFilepattern(pattern).call();
        LOG.debug("{} changes are about to be commited", added.getEntryCount());
        RevCommit commit = git.commit().setMessage(commitMessage).call();
        revision = new Revision(commit.getName(), commit.getShortMessage(), commit.getCommitTime());
      } else {
        LOG.debug("No changes found {}", pattern);
      }
    } catch (GitAPIException e) {
      LOG.error("Failed to add+comit {} to Git", pattern, e);
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
  public synchronized Note get(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    Note note = null;
    RevCommit stash = null;
    try {
      List<DiffEntry> gitDiff = git.diff().setPathFilter(PathFilter.create(noteId)).call();
      boolean modified = !gitDiff.isEmpty();
      if (modified) {
        // stash changes
        stash = git.stashCreate().call();
        Collection<RevCommit> stashes = git.stashList().call();
        LOG.debug("Created stash : {}, stash size : {}", stash, stashes.size());
      }
      ObjectId head = git.getRepository().resolve(Constants.HEAD);
      // checkout to target revision
      git.checkout().setStartPoint(revId).addPath(noteId).call();
      // get the note
      note = super.get(noteId, subject);
      // checkout back to head
      git.checkout().setStartPoint(head.getName()).addPath(noteId).call();
      if (modified && stash != null) {
        // unstash changes
        ObjectId applied = git.stashApply().setStashRef(stash.getName()).call();
        ObjectId dropped = git.stashDrop().setStashRef(0).call();
        Collection<RevCommit> stashes = git.stashList().call();
        LOG.debug("Stash applied as : {}, and dropped : {}, stash size: {}", applied, dropped,
            stashes.size());
      }
    } catch (GitAPIException e) {
      LOG.error("Failed to return note from revision \"{}\"", revId, e);
    }
    return note;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    List<Revision> history = Lists.newArrayList();
    LOG.debug("Listing history for {}:", noteId);
    try {
      Iterable<RevCommit> logs = git.log().addPath(noteId).call();
      for (RevCommit log: logs) {
        history.add(new Revision(log.getName(), log.getShortMessage(), log.getCommitTime()));
        LOG.debug(" - ({},{},{})", log.getName(), log.getCommitTime(), log.getFullMessage());
      }
    } catch (NoHeadException e) {
      //when no initial commit exists
      LOG.warn("No Head found for {}, {}", noteId, e.getMessage());
    } catch (GitAPIException e) {
      LOG.error("Failed to get logs for {}", noteId, e);
    }
    return history;
  }

  @Override
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    Note revisionNote = get(noteId, revId, subject);
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
  Git getGit() {
    return git;
  }

  void setGit(Git git) {
    this.git = git;
  }

}
