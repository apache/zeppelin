package org.apache.zeppelin.interpreter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 *
 * Util class for creating a Mini Hadoop cluster in local machine to test scenarios that needs
 * hadoop cluster.
 */
public class MiniHadoopCluster {

  private static Logger LOGGER = LoggerFactory.getLogger(MiniHadoopCluster.class);

  private Configuration hadoopConf;
  private MiniDFSCluster dfsCluster;
  private MiniYARNCluster yarnCluster;
  private String configPath = new File("target/tests/hadoop_conf").getAbsolutePath();

  @BeforeClass
  public void start() throws IOException {
    LOGGER.info("Starting MiniHadoopCluster ...");
    this.hadoopConf = new Configuration();
    new File(configPath).mkdirs();
    // start MiniDFSCluster
    this.dfsCluster = new MiniDFSCluster.Builder(hadoopConf)
        .numDataNodes(2)
        .format(true)
        .waitSafeMode(true)
        .build();
    this.dfsCluster.waitActive();
    saveConfig(hadoopConf, configPath + "/core-site.xml");

    // start MiniYarnCluster
    YarnConfiguration baseConfig = new YarnConfiguration(hadoopConf);
    baseConfig.set("yarn.nodemanager.disk-health-checker.max-disk-utilization-per-disk-percentage", "95");
    this.yarnCluster = new MiniYARNCluster(getClass().getName(), 2,
        1, 1);
    yarnCluster.init(baseConfig);

    // Install a shutdown hook for stop the service and kill all running applications.
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        yarnCluster.stop();
      }
    });

    yarnCluster.start();

    // Workaround for YARN-2642.
    Configuration yarnConfig = yarnCluster.getConfig();
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 30 * 1000) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
      if (!yarnConfig.get(YarnConfiguration.RM_ADDRESS).split(":")[1].equals("0")) {
        break;
      }
    }
    if (yarnConfig.get(YarnConfiguration.RM_ADDRESS).split(":")[1].equals("0")) {
      throw new IOException("RM not up yes");
    }

    LOGGER.info("RM address in configuration is " + yarnConfig.get(YarnConfiguration.RM_ADDRESS));
    saveConfig(yarnConfig,configPath + "/yarn-site.xml");
  }

  protected void saveConfig(Configuration conf, String dest) throws IOException {
    Configuration redacted = new Configuration(conf);
    // This setting references a test class that is not available when using a real Spark
    // installation, so remove it from client configs.
    redacted.unset("net.topology.node.switch.mapping.impl");

    FileOutputStream out = new FileOutputStream(dest);
    try {
      redacted.writeXml(out);
    } finally {
      out.close();
    }
    LOGGER.info("Save configuration to " + dest);
  }

  @AfterClass
  public void stop() {
    if (this.yarnCluster != null) {
      this.yarnCluster.stop();
    }
    if (this.dfsCluster != null) {
      this.dfsCluster.shutdown();
    }
  }

  public String getConfigPath() {
    return configPath;
  }

  public MiniYARNCluster getYarnCluster() {
    return yarnCluster;
  }
}
