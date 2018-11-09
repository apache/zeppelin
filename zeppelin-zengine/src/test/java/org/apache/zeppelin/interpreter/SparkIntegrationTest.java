package org.apache.zeppelin.interpreter;

import org.apache.commons.io.IOUtils;
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
public class SparkIntegrationTest {
  private static Logger LOGGER = LoggerFactory.getLogger(SparkIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelin zeppelin;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  private String sparkVersion;
  private String sparkHome;

  public SparkIntegrationTest(String sparkVersion) {
    LOGGER.info("Testing SparkVersion: " + sparkVersion);
    this.sparkVersion = sparkVersion;
    this.sparkHome = SparkDownloadUtils.downloadSpark(sparkVersion);
  }

  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {"2.4.0"},
            {"2.3.2"},
            {"2.2.1"},
            {"2.1.2"},
            {"2.0.2"},
            {"1.6.3"}
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
    // test SparkInterpreter
    interpreterSettingManager.setInterpreterBinding("user1", "note1", interpreterSettingManager.getInterpreterSettingIds());
    Interpreter sparkInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.spark");

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = sparkInterpreter.interpret("sc.version", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    String detectedSparkVersion = interpreterResult.message().get(0).getData();
    assertTrue(detectedSparkVersion +" doesn't contain " + this.sparkVersion, detectedSparkVersion.contains(this.sparkVersion));
    interpreterResult = sparkInterpreter.interpret("sc.range(1,10).sum()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    assertTrue(interpreterResult.msg.get(0).getData().contains("45"));

    // test PySparkInterpreter
    Interpreter pySparkInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.pyspark");
    interpreterResult = pySparkInterpreter.interpret("sqlContext.createDataFrame([(1,'a'),(2,'b')], ['id','name']).registerTempTable('test')", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);

    // test IPySparkInterpreter
    Interpreter ipySparkInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.ipyspark");
    interpreterResult = ipySparkInterpreter.interpret("sqlContext.table('test').show()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);

    // test SparkSQLInterpreter
    Interpreter sqlInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.sql");
    interpreterResult = sqlInterpreter.interpret("select count(1) as c from test", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("c\n2\n", interpreterResult.message().get(0).getData());

    // test SparkRInterpreter
    Interpreter sparkrInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.r");
    if (isSpark2()) {
      interpreterResult = sparkrInterpreter.interpret("df <- as.DataFrame(faithful)\nhead(df)", context);
    } else {
      interpreterResult = sparkrInterpreter.interpret("df <- createDataFrame(sqlContext, faithful)\nhead(df)", context);
    }
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.message().get(0).getType());
    assertTrue(interpreterResult.message().get(0).getData().contains("eruptions waiting"));
  }

  @Test
  public void testLocalMode() throws IOException, YarnException, InterpreterException, InterruptedException {
    InterpreterSetting sparkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark");
    sparkInterpreterSetting.setProperty("master", "local[*]");
    sparkInterpreterSetting.setProperty("SPARK_HOME", sparkHome);
    sparkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zeppelin.getZeppelinConfDir().getAbsolutePath());
    sparkInterpreterSetting.setProperty("zeppelin.spark.useHiveContext", "false");
    sparkInterpreterSetting.setProperty("zeppelin.pyspark.useIPython", "false");
    sparkInterpreterSetting.setProperty("spark.pyspark.python", getPythonExec());

    testInterpreterBasics();

    // no yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(0, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  @Test
  public void testYarnClientMode() throws IOException, YarnException, InterruptedException, InterpreterException {
    InterpreterSetting sparkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark");
    sparkInterpreterSetting.setProperty("master", "yarn-client");
    sparkInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    sparkInterpreterSetting.setProperty("SPARK_HOME", sparkHome);
    sparkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zeppelin.getZeppelinConfDir().getAbsolutePath());
    sparkInterpreterSetting.setProperty("zeppelin.spark.useHiveContext", "false");
    sparkInterpreterSetting.setProperty("zeppelin.pyspark.useIPython", "false");
    sparkInterpreterSetting.setProperty("spark.pyspark.python", getPythonExec());
    sparkInterpreterSetting.setProperty("spark.driver.memory", "512m");

    testInterpreterBasics();

    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  @Test
  public void testYarnClusterMode() throws IOException, YarnException, InterruptedException, InterpreterException {
    InterpreterSetting sparkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark");
    sparkInterpreterSetting.setProperty("master", "yarn-cluster");
    sparkInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    sparkInterpreterSetting.setProperty("SPARK_HOME", sparkHome);
    sparkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zeppelin.getZeppelinConfDir().getAbsolutePath());
    sparkInterpreterSetting.setProperty("zeppelin.spark.useHiveContext", "false");
    sparkInterpreterSetting.setProperty("zeppelin.pyspark.useIPython", "false");
    sparkInterpreterSetting.setProperty("spark.pyspark.python", getPythonExec());
    sparkInterpreterSetting.setProperty("spark.driver.memory", "512m");

    testInterpreterBasics();

    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  private boolean isSpark2() {
    return this.sparkVersion.startsWith("2.");
  }

  private String getPythonExec() throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec(new String[]{"which", "python"});
    if (process.waitFor() != 0) {
      throw new RuntimeException("Fail to run command: which python.");
    }
    return IOUtils.toString(process.getInputStream()).trim();
  }
}
