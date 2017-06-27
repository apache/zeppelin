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
package org.apache.zeppelin.users;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Class defining information about user.
 */
public class UserInfo {
  private static final int MAX_RECENT_SIZE = 10;

  /**
   * Last viewed notes (ids).
   * Notes are added at the end (the last viewed note at the end of the list).
   */
  private LinkedHashSet<String> recentNotes = new LinkedHashSet<>();

  /**
   * Returns ids of last viewed notes.
   * @return list of ids of last viewed notes
   */
  public List<String> getRecentNotesIds() {
    return new ArrayList<>(recentNotes);
  }

  public void addRecentNote(String noteId) {
    synchronized (recentNotes) {
      if (recentNotes.contains(noteId)) {
        recentNotes.remove(noteId);
      }
      recentNotes.add(noteId);
      if (recentNotes.size() >= MAX_RECENT_SIZE) {
        recentNotes.remove(recentNotes.iterator().next());
      }
    }
  }

  public void removeNoteFromRecent(String noteId) {
    recentNotes.remove(noteId);
  }

  public void clearRecent() {
    recentNotes.clear();
  }
}
