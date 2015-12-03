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
import java.util.List;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
public class GitNotebookRepo extends VFSNotebookRepo implements NotebookRepoVersioned {
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
    maybeAddAndCommit(".");
  }

  @Override
  public synchronized void save(Note note) throws IOException {
    super.save(note);
    maybeAddAndCommit(note.getId());
  }

  private void maybeAddAndCommit(String pattern) {
    try {
      List<DiffEntry> gitDiff = git.diff().call();
      if (!gitDiff.isEmpty()) {
        LOG.debug("Changes found for pattern '{}': {}", pattern, gitDiff);
        DirCache added = git.add().addFilepattern(pattern).call();
        LOG.debug("{} changes are about to be commited", added.getEntryCount());
        git.commit().setMessage("Updated " + pattern).call();
      } else {
        LOG.debug("No changes found {}", pattern);
      }
    } catch (GitAPIException e) {
      LOG.error("Faild to add+comit {} to Git", pattern, e);
    }
  }

  @Override
  public Note get(String noteId, String rev) throws IOException {
    //TODO(bzz): something like 'git checkout rev', that will not change-the-world though
    return super.get(noteId);
  }

  @Override
  public List<Rev> history(String noteId) {
    List<Rev> history = Lists.newArrayList();
    LOG.debug("Listing history for {}:", noteId);
    try {
      Iterable<RevCommit> logs = git.log().addPath(noteId).call();
      for (RevCommit log: logs) {
        history.add(new Rev(log.getName(), log.getCommitTime()));
        LOG.debug(" - ({},{})", log.getName(), log.getCommitTime());
      }
    } catch (GitAPIException e) {
      LOG.error("Failed to get logs for {}", noteId, e);
    }
    return history;
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
