package org.apache.zeppelin.interpreter;

import static org.mockito.Mockito.mock;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.notebook.Note;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will load configuration files under src/test/resources/interpreter
 * src/test/resources/conf
 *
 * <p>to construct InterpreterSettingManager and InterpreterFactory properly
 */
public abstract class AbstractInterpreterTest {
  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractInterpreterTest.class);

  protected InterpreterSettingManager interpreterSettingManager;
  protected InterpreterFactory interpreterFactory;
  protected File zeppelinHome;
  protected File interpreterDir;
  protected File confDir;
  protected File notebookDir;
  protected ZeppelinConfiguration conf;

  @Before
  public void setUp() throws Exception {
    // copy the resources files to a temp folder
    zeppelinHome = new File("..");
    LOGGER.info("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
    interpreterDir = new File(zeppelinHome, "interpreter_" + getClass().getSimpleName());
    confDir = new File(zeppelinHome, "conf_" + getClass().getSimpleName());
    notebookDir = new File(zeppelinHome, "notebook_" + getClass().getSimpleName());

    interpreterDir.mkdirs();
    confDir.mkdirs();
    notebookDir.mkdirs();

    FileUtils.copyDirectory(new File("src/test/resources/interpreter"), interpreterDir);
    FileUtils.copyDirectory(new File("src/test/resources/conf"), confDir);

    System.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), zeppelinHome.getAbsolutePath());
    System.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(), confDir.getAbsolutePath());
    System.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_DIR.getVarName(),
        interpreterDir.getAbsolutePath());
    System.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir.getAbsolutePath());
    System.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName(), "test");

    conf = new ZeppelinConfiguration();
    interpreterSettingManager =
        new InterpreterSettingManager(
            conf,
            mock(AngularObjectRegistryListener.class),
            mock(RemoteInterpreterProcessListener.class),
            mock(ApplicationEventListener.class));
    interpreterFactory = new InterpreterFactory(interpreterSettingManager);
  }

  @After
  public void tearDown() throws Exception {
    interpreterSettingManager.close();
    FileUtils.deleteDirectory(interpreterDir);
    FileUtils.deleteDirectory(confDir);
    FileUtils.deleteDirectory(notebookDir);
  }

  protected Note createNote() {
    return new Note(
        "test",
        "test",
        null,
        interpreterFactory,
        interpreterSettingManager,
        null,
        null,
        null,
        null);
  }

  protected InterpreterContext createDummyInterpreterContext() {
    return InterpreterContext.builder().setNoteId("noteId").setParagraphId("paragraphId").build();
  }
}
