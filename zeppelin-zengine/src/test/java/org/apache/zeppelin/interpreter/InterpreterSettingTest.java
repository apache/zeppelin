package org.apache.zeppelin.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class InterpreterSettingTest {

  @Test
  public void sharedModeCloseandRemoveInterpreterGroupTest() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SHARED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    // This won't effect anything
    Interpreter mockInterpreter2 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList2 = new ArrayList<>();
    interpreterList2.add(mockInterpreter2);
    interpreterGroup = interpreterSetting.getInterpreterGroup("user2", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user2", "note1"), interpreterList2);

    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void perUserScopedModeCloseAndRemoveInterpreterGroupTest() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    Interpreter mockInterpreter2 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList2 = new ArrayList<>();
    interpreterList2.add(mockInterpreter2);
    interpreterGroup = interpreterSetting.getInterpreterGroup("user2", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user2", "note1"), interpreterList2);

    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", "note1").size());
    assertEquals(2, interpreterSetting.getInterpreterGroup("user2", "note1").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertEquals(1, interpreterSetting.getInterpreterGroup("user2","note1").size());

    // Check if non-existed key works or not
    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertEquals(1, interpreterSetting.getInterpreterGroup("user2","note1").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void perUserIsolatedModeCloseAndRemoveInterpreterGroupTest() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    Interpreter mockInterpreter2 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList2 = new ArrayList<>();
    interpreterList2.add(mockInterpreter2);
    interpreterGroup = interpreterSetting.getInterpreterGroup("user2", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user2", "note1"), interpreterList2);

    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user2", "note1").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertEquals(1, interpreterSetting.getInterpreterGroup("user2","note1").size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void perNoteScopedModeCloseAndRemoveInterpreterGroupTest() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    Interpreter mockInterpreter2 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList2 = new ArrayList<>();
    interpreterList2.add(mockInterpreter2);
    interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note2");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note2"), interpreterList2);

    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", "note1").size());
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", "note2").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1","note2").size());

    // Check if non-existed key works or not
    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1","note2").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note2", "user1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void perNoteIsolatedModeCloseAndRemoveInterpreterGroupTest() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.ISOLATED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    Interpreter mockInterpreter2 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList2 = new ArrayList<>();
    interpreterList2.add(mockInterpreter2);
    interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note2");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note2"), interpreterList2);

    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note2").size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1","note2").size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSetting.closeAndRemoveInterpreterGroup("note2", "user1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void perNoteScopedModeRemoveInterpreterGroupWhenNoteIsRemoved() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());

    // This method will be called when remove note
    interpreterSetting.closeAndRemoveInterpreterGroup("note1","");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
    // Be careful that getInterpreterGroup makes interpreterGroup if it doesn't exist
    assertEquals(0, interpreterSetting.getInterpreterGroup("user1","note1").size());
  }

  @Test
  public void perNoteIsolatedModeRemoveInterpreterGroupWhenNoteIsRemoved() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.ISOLATED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());

    // This method will be called when remove note
    interpreterSetting.closeAndRemoveInterpreterGroup("note1","");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
    // Be careful that getInterpreterGroup makes interpreterGroup if it doesn't exist
    assertEquals(0, interpreterSetting.getInterpreterGroup("user1","note1").size());
  }

  @Test
  public void perUserScopedModeNeverRemoveInterpreterGroupWhenNoteIsRemoved() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());

    // This method will be called when remove note
    interpreterSetting.closeAndRemoveInterpreterGroup("note1","");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    // Be careful that getInterpreterGroup makes interpreterGroup if it doesn't exist
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1","note1").size());
  }

  @Test
  public void perUserIsolatedModeNeverRemoveInterpreterGroupWhenNoteIsRemoved() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    InterpreterSetting interpreterSetting = new InterpreterSetting("", "", "", new ArrayList<InterpreterInfo>(), new Properties(), new ArrayList<Dependency>(), interpreterOption, "", null);

    interpreterSetting.setInterpreterGroupFactory(new InterpreterGroupFactory() {
      @Override
      public InterpreterGroup createInterpreterGroup(String interpreterGroupId,
          InterpreterOption option) {
        return new InterpreterGroup(interpreterGroupId);
      }
    });

    Interpreter mockInterpreter1 = mock(RemoteInterpreter.class);
    List<Interpreter> interpreterList1 = new ArrayList<>();
    interpreterList1.add(mockInterpreter1);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    interpreterGroup.put(interpreterSetting.getInterpreterSessionKey("user1", "note1"), interpreterList1);

    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").size());

    // This method will be called when remove note
    interpreterSetting.closeAndRemoveInterpreterGroup("note1","");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    // Be careful that getInterpreterGroup makes interpreterGroup if it doesn't exist
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1","note1").size());
  }
}
