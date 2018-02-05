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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.Selectors;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
*
*/
public class VFSNotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(VFSNotebookRepo.class);

  private ZeppelinConfiguration conf;
  private NotebookRepoCommon commons;

  public VFSNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    commons = new NotebookRepoCommon(conf);
    commons.setNotebookDirectory(conf.getNotebookDir());
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    return commons.list(subject);
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    return commons.get(noteId, subject);
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    commons.save(note, subject);
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    FileObject rootDir = commons.getFileSystemManager().resolveFile(commons.getPath("/"));
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);

    if (!noteDir.exists()) {
      // nothing to do
      return;
    }

    if (!commons.isDirectory(noteDir)) {
      // it is not look like zeppelin note savings
      throw new IOException("Can not remove " + noteDir.getName().toString());
    }

    noteDir.delete(Selectors.SELECT_SELF_AND_CHILDREN);
  }

  @Override
  public void close() {
    //no-op    
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    return commons.getSettings(subject);
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    commons.updateSettings(settings, subject);
  }

}
