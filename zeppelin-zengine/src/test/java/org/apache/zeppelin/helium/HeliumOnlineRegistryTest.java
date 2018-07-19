package org.apache.zeppelin.helium;

import static org.junit.Assert.assertTrue;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;


public class HeliumOnlineRegistryTest {
  // ip 192.168.65.17 belongs to private network
  // request will be ended with connection time out error
  private static final String IP = "192.168.65.17";
  private static final String TIMEOUT = "2000";

  private File tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(
            System.getProperty("java.io.tmpdir")
                    + "/ZeppelinLTest_"
                    + System.currentTimeMillis()
    );
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(tmpDir);
  }

  @Test
  public void zeppelinNotebookS3TimeoutPropertyTest() throws IOException {
    System.setProperty(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_TIMEOUT.getVarName(),
            TIMEOUT
    );
    System.setProperty(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT.getVarName(),
            IP
    );
    HeliumOnlineRegistry heliumOnlineRegistry = new HeliumOnlineRegistry(
            "https://" + IP,
            "https://" + IP,
            tmpDir
    );

    long start = System.currentTimeMillis();
    heliumOnlineRegistry.getAll();
    long processTime = System.currentTimeMillis() - start;

    long basicTimeout = Long.valueOf(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_TIMEOUT.getStringValue()
    );
    assertTrue(
            String.format(
                    "Wrong timeout during connection: expected %s, actual is about %d",
                    TIMEOUT,
                    processTime
            ),
            basicTimeout > processTime
    );
  }
}
