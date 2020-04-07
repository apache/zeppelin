package org.apache.zeppelin.interpreter.recovery;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class FileSystemRecoveryStorageTest extends AbstractInterpreterTest {

  private File recoveryDir = null;

  @Before
  public void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getVarName(),
        FileSystemRecoveryStorage.class.getName());
    recoveryDir = Files.createTempDir();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_DIR.getVarName(), recoveryDir.getAbsolutePath());
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    FileUtils.deleteDirectory(recoveryDir);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getVarName(),
            ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getStringValue());
  }

  @Test
  public void testSingleInterpreterProcess() throws InterpreterException, IOException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;
    InterpreterContext context1 = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .build();
    remoteInterpreter1.interpret("hello", context1);

    assertEquals(1, interpreterSettingManager.getRecoveryStorage().restore().size());

    interpreterSetting.close();
    assertEquals(0, interpreterSettingManager.getRecoveryStorage().restore().size());
  }

  @Test
  public void testMultipleInterpreterProcess() throws InterpreterException, IOException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser(InterpreterOption.ISOLATED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;
    InterpreterContext context1 = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .build();
    remoteInterpreter1.interpret("hello", context1);
    assertEquals(1, interpreterSettingManager.getRecoveryStorage().restore().size());

    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user2", "note2");
    RemoteInterpreter remoteInterpreter2 = (RemoteInterpreter) interpreter2;
    InterpreterContext context2 = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .build();
    remoteInterpreter2.interpret("hello", context2);

    assertEquals(2, interpreterSettingManager.getRecoveryStorage().restore().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "note1", "user1");
    assertEquals(1, interpreterSettingManager.getRecoveryStorage().restore().size());

    interpreterSetting.close();
    assertEquals(0, interpreterSettingManager.getRecoveryStorage().restore().size());
  }

}
