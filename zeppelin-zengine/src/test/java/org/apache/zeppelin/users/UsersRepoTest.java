package org.apache.zeppelin.users;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.*;

public class UsersRepoTest {
  @Test
  public void putRecentNote() throws IOException {
    UsersRepo usersRepo = getTestUsersRepo();
    usersRepo.putRecentNote("user", "1");
    assertTrue(usersRepo.getUserInfo("user").getRecentNotesIds().contains("1"));
  }

  @Test
  public void removeNoteFromRecent() throws IOException {
    UsersRepo usersRepo = getTestUsersRepo();
    usersRepo.putRecentNote("user1", "1");
    usersRepo.putRecentNote("user1", "2");
    usersRepo.putRecentNote("user1", "3");
    usersRepo.putRecentNote("user2", "1");
    usersRepo.putRecentNote("user2", "2");
    usersRepo.putRecentNote("user2", "3");
    usersRepo.removeNoteFromRecent("user1", "2");
    assertFalse(usersRepo.getUserInfo("user1").getRecentNotesIds().contains("2"));
    assertTrue(usersRepo.getUserInfo("user2").getRecentNotesIds().contains("2"));
  }

  @Test
  public void removeNoteFromRecentToAllUsers() throws Exception {
    UsersRepo usersRepo = getTestUsersRepo();
    usersRepo.putRecentNote("user1", "1");
    usersRepo.putRecentNote("user1", "2");
    usersRepo.putRecentNote("user1", "3");
    usersRepo.putRecentNote("user2", "1");
    usersRepo.putRecentNote("user2", "2");
    usersRepo.putRecentNote("user2", "3");
    usersRepo.removeNoteFromRecent("2");
    assertFalse(usersRepo.getUserInfo("user1").getRecentNotesIds().contains("2"));
    assertFalse(usersRepo.getUserInfo("user2").getRecentNotesIds().contains("2"));
  }

  @Test
  public void clearRecent() throws IOException {
    UsersRepo usersRepo = getTestUsersRepo();
    usersRepo.putRecentNote("user", "1");
    usersRepo.putRecentNote("user", "2");
    usersRepo.putRecentNote("user", "3");
    usersRepo.clearRecent("user");
    assertTrue(usersRepo.getUserInfo("user").getRecentNotesIds().size() == 0);
  }

  private UsersRepo getTestUsersRepo(){
    return new UsersRepo();
  }

}