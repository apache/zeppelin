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

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.*;

public class RecentNotesRepoTest {
  @Test
  public void putRecentNote() throws IOException {
    RecentNotesRepo recentNotesRepo = getTestUsersRepo();
    recentNotesRepo.putRecentNote("user", "1");
    assertTrue(recentNotesRepo.getRecentNotesInfo("user").getRecentNotesIds().contains("1"));
  }

  @Test
  public void removeNoteFromRecent() throws IOException {
    RecentNotesRepo recentNotesRepo = getTestUsersRepo();
    recentNotesRepo.putRecentNote("user1", "1");
    recentNotesRepo.putRecentNote("user1", "2");
    recentNotesRepo.putRecentNote("user1", "3");
    recentNotesRepo.putRecentNote("user2", "1");
    recentNotesRepo.putRecentNote("user2", "2");
    recentNotesRepo.putRecentNote("user2", "3");
    recentNotesRepo.removeNoteFromRecent("user1", "2");
    assertFalse(recentNotesRepo.getRecentNotesInfo("user1").getRecentNotesIds().contains("2"));
    assertTrue(recentNotesRepo.getRecentNotesInfo("user2").getRecentNotesIds().contains("2"));
  }

  @Test
  public void removeNoteFromRecentToAllUsers() throws Exception {
    RecentNotesRepo recentNotesRepo = getTestUsersRepo();
    recentNotesRepo.putRecentNote("user1", "1");
    recentNotesRepo.putRecentNote("user1", "2");
    recentNotesRepo.putRecentNote("user1", "3");
    recentNotesRepo.putRecentNote("user2", "1");
    recentNotesRepo.putRecentNote("user2", "2");
    recentNotesRepo.putRecentNote("user2", "3");
    recentNotesRepo.removeNoteFromRecent("2");
    assertFalse(recentNotesRepo.getRecentNotesInfo("user1").getRecentNotesIds().contains("2"));
    assertFalse(recentNotesRepo.getRecentNotesInfo("user2").getRecentNotesIds().contains("2"));
  }

  @Test
  public void clearRecent() throws IOException {
    RecentNotesRepo recentNotesRepo = getTestUsersRepo();
    recentNotesRepo.putRecentNote("user", "1");
    recentNotesRepo.putRecentNote("user", "2");
    recentNotesRepo.putRecentNote("user", "3");
    recentNotesRepo.clearRecent("user");
    assertTrue(recentNotesRepo.getRecentNotesInfo("user").getRecentNotesIds().size() == 0);
  }

  private RecentNotesRepo getTestUsersRepo(){
    return new RecentNotesRepo();
  }

}