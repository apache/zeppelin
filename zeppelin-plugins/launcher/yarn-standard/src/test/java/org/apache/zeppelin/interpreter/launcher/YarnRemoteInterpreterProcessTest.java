package org.apache.zeppelin.interpreter.launcher;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YarnRemoteInterpreterProcessTest {
  @Before
  public void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }
  }

  @Test
  public void testLauncher() throws IOException {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    zConf.setServerKerberosKeytab("keytab_value");
    zConf.setServerKerberosPrincipal("Principal_value");
    zConf.setYarnWebappAddress("http://127.0.0.1");

    YarnStandardInterpreterLauncher.setTest(true);
    YarnStandardInterpreterLauncher launcher = new YarnStandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intp_Group-Id", "groupId", "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof YarnRemoteInterpreterProcess);
    YarnRemoteInterpreterProcess interpreterProcess = (YarnRemoteInterpreterProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/groupName", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertTrue(interpreterProcess.getConnectTimeout()>= 200000);
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(9, interpreterProcess.getEnv().size());
    assertEquals("yarn", interpreterProcess.getEnv().get("ZEPPELIN_RUN_MODE"));
    assertEquals("apache/zeppelin:0.9.0-SNAPSHOT", interpreterProcess.getEnv().get("ZEPPELIN_YARN_CONTAINER_IMAGE"));
    assertEquals("intp-group-id", interpreterProcess.getEnv().get("YARN_APP_NAME"));
    assertEquals("keytab_value", interpreterProcess.getEnv().get("SUBMARINE_HADOOP_KEYTAB"));
    assertEquals("Principal_value", interpreterProcess.getEnv().get("SUBMARINE_HADOOP_PRINCIPAL"));
    assertEquals("groupId", interpreterProcess.getEnv().get("INTERPRETER_GROUP_ID"));
    assertEquals("--localization \":/zeppelin/interpreter/name:rw\" --localization \":/zeppelin/lib/interpreter:rw\" --localization \":/zeppelin/conf/log4j.properties:rw\" --localization \"keytab_value:keytab_value:rw\" ", interpreterProcess.getEnv().get("YARN_LOCALIZATION_ENV"));
    assertEquals("--env ZEPPELIN_CONF_DIR=/zeppelin", interpreterProcess.getEnv().get("ZEPPELIN_CONF_DIR_ENV"));
    assertEquals("memory=8G,vcores=1,gpu=0", interpreterProcess.getEnv().get("ZEPPELIN_YARN_CONTAINER_RESOURCE"));
    assertEquals(true, interpreterProcess.isUserImpersonated());
  }
}
