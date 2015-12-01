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
import java.util.Collections;
import java.util.List;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

/**
 * NotebookRepo that hosts all the notebook FS in a single Git repo
 *
 * This impl intended to be simple and straightforward:
 *   - does not handle branches
 *   - only basic git, no Github push\pull yet
 *
 *
 * TODO(bzz): describe config
 *  GIT_REMOTE_URL remote
 *  auth credentials
 */
public class GitNotebookRepo extends VFSNotebookRepo implements NotebookRepoVersioned {
  private static final Logger LOG = LoggerFactory.getLogger(GitNotebookRepo.class);

  private String localPath;
  private Git git;

  // I. First useful case:
  //   start \w repo + tutorial notebook
  //   all modifications results in a commit

  // II. Next case:
  //   start \wo .git
  //   create one
  //   add existing notebooks
  //   ..and then case I. ...

  // III. Next case:
  //   start \w repo
  //   show history
  //   user can switch to REV in read-only

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
        LOG.info("Changes found for pattern {}: {}", pattern, gitDiff);
        DirCache added = git.add().addFilepattern(pattern).call();
        LOG.info("{} changes area about to be commited", added.getEntryCount());
        git.commit().setMessage("Updated " + pattern).call();
      } else {
        LOG.info("No changes found {}", pattern);
      }
    } catch (GitAPIException e) {
      LOG.error("Faild to add+comit {} to Git", pattern, e);
    }
  }

  @Override
  public Note get(String noteId, String rev) throws IOException {
    //TODO(alex): something instead of 'git checkout rev', that will not change-the-world
    return super.get(noteId);
  }

  @Override
  public List<String> history(String noteId) {
    //TODO(alex): git logs -- "noteId"
    return Collections.emptyList();
  }

  //DI replacements for Tests
  Git getGit() {
    return git;
  }

  void setGit(Git git) {
    this.git = git;
  }

}
