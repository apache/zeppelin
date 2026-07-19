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
import java.io.OutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.NameScope;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Test-only subclass of {@link VFSNotebookRepo} that injects an artificial delay after the
 * destination file name has been resolved in {@code save()} and after {@code move()} starts.
 * This reproduces the ZEPPELIN-5858 moveNote/saveNote race condition: a concurrent move
 * (rename) and save on the same note can both write a {@code {oldPath}_{noteId}.zpln} and a
 * {@code {newPath}_{noteId}.zpln} file, leaving a duplicated noteId in the repo.
 */
public class VFSNotebookRepoWithDelay extends VFSNotebookRepo {

  private final long delayInMillis;

  public VFSNotebookRepoWithDelay(long delayInMillis) {
    this.delayInMillis = delayInMillis;
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    // write to tmp file first, then rename it to the {note_name}_{note_id}.zpln
    FileObject noteJson = rootNotebookFileObject.resolveFile(
        buildNoteTempFileName(note), NameScope.DESCENDENT);
    OutputStream out = null;
    try {
      out = noteJson.getContent().getOutputStream(false);
      IOUtils.write(note.toJson().getBytes(zConf.getString(ConfVars.ZEPPELIN_ENCODING)), out);
    } finally {
      if (out != null) {
        out.close();
      }
    }
    // Destination file name is captured before the delay, simulating a network round trip
    // that happens after the note path has already been read. This ordering is the essence
    // of the race: capturing after the delay would not reproduce it.
    String noteFileName = buildNoteFileName(note);
    delay();
    noteJson.moveTo(rootNotebookFileObject.resolveFile(noteFileName, NameScope.DESCENDENT));
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
      AuthenticationInfo subject) throws IOException {
    // Delay at the start simulates a slow remote repo, widening the window for a concurrent
    // save to race with this move.
    delay();
    super.move(noteId, notePath, newNotePath, subject);
  }

  private void delay() {
    try {
      Thread.sleep(delayInMillis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
