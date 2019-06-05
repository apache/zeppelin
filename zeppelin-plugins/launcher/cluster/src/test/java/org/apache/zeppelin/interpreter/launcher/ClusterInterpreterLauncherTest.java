package org.apache.zeppelin.interpreter.launcher;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClusterInterpreterLauncherTest extends ClusterMockTest {

  @BeforeClass
  public static void startTest() throws IOException, InterruptedException {
    ClusterMockTest.startCluster();
  }

  @AfterClass
  public static void stopTest() throws IOException, InterruptedException {
    ClusterMockTest.stopCluster();
  }

  @Before
  public void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }
  }

  @Test
  public void testConnectExistIntpProcess() throws IOException {
    mockIntpProcessMeta("intpGroupId");

    ClusterInterpreterLauncher launcher
        = new ClusterInterpreterLauncher(ClusterMockTest.zconf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId", "groupId",
        "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof RemoteInterpreterRunningProcess);
    RemoteInterpreterRunningProcess interpreterProcess = (RemoteInterpreterRunningProcess) client;
    assertEquals("INTP_TSERVER_HOST", interpreterProcess.getHost());
    assertEquals(0, interpreterProcess.getPort());
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(5000, interpreterProcess.getConnectTimeout());
  }

  @Test
  public void testCreateIntpProcess() throws IOException {
    ClusterInterpreterLauncher launcher
        = new ClusterInterpreterLauncher(ClusterMockTest.zconf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId", "groupId",
        "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/groupName", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(5000, interpreterProcess.getConnectTimeout());
    assertEquals(zconf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 1);
    assertEquals(true, interpreterProcess.isUserImpersonated());
  }
}
