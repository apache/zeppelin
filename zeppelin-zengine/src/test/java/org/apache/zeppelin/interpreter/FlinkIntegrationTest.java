package org.apache.zeppelin.interpreter;

import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(value = Parameterized.class)
public class FlinkIntegrationTest {
  private static Logger LOGGER = LoggerFactory.getLogger(SparkIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelin zeppelin;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  private String flinkVersion;
  private String flinkHome;

  public FlinkIntegrationTest(String flinkVersion) {
    LOGGER.info("Testing FlinkVersion: " + flinkVersion);
    this.flinkVersion = flinkVersion;
    this.flinkHome = SparkDownloadUtils.downloadFlink(flinkVersion);
  }

  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {"1.5.1"},
        {"1.5.2"}
    });

  }

  @BeforeClass
  public static void setUp() throws IOException {
    hadoopCluster = new MiniHadoopCluster();
    hadoopCluster.start();

    zeppelin = new MiniZeppelin();
    zeppelin.start();
    interpreterFactory = zeppelin.getInterpreterFactory();
    interpreterSettingManager = zeppelin.getInterpreterSettingManager();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    if (zeppelin != null) {
      zeppelin.stop();
    }
    if (hadoopCluster != null) {
      hadoopCluster.stop();
    }
  }

  private void testInterpreterBasics() throws IOException, InterpreterException {
    // test FlinkInterpreter
    Interpreter flinkInterpreter = interpreterFactory.getInterpreter("user1", "note1", "flink", "flink");

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = flinkInterpreter.interpret("1+1", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    assertTrue(interpreterResult.msg.get(0).getData().contains("2"));

  }

  @Test
  public void testLocalMode() throws IOException, YarnException, InterpreterException {
    InterpreterSetting flinkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("flink");
    flinkInterpreterSetting.setProperty("FLINK_HOME", flinkHome);
    flinkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zeppelin.getZeppelinConfDir().getAbsolutePath());

    testInterpreterBasics();

    // no yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(0, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  // TODO(zjffdu) enable it when make yarn integration test work
  //  @Test
  public void testYarnMode() throws IOException, InterpreterException, YarnException {
    InterpreterSetting flinkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("flink");
    flinkInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    flinkInterpreterSetting.setProperty("FLINK_HOME", flinkHome);
    flinkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zeppelin.getZeppelinConfDir().getAbsolutePath());
    flinkInterpreterSetting.setProperty("flink.execution.mode", "YARN");
    testInterpreterBasics();

    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());

    interpreterSettingManager.close();
  }
}
