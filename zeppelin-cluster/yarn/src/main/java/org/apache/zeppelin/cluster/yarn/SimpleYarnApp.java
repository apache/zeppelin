package org.apache.zeppelin.cluster.yarn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class SimpleYarnApp {
  private static final Logger logger = LoggerFactory.getLogger(SimpleYarnApp.class);

  private String appName = "SIMPLE";

  private String appMasterJar = "/Users/jl/local/src/g/zeppelin/zeppelin-cluster/yarn/target/"
      + "zeppelin-cluster-yarn-0.8.0-SNAPSHOT.jar";

  private String appMasterJarPath = "zeppelin-cluster.jar";

  private String appMasterMainClass = "org.apache.zeppelin.cluster.yarn.ApplicationMaster";

  public static void main(String[] args) throws Exception {
    new SimpleYarnApp().run();
  }

  public void run() throws Exception {
    boolean keepContainers = false;
    String appName = "test";
    Configuration conf = new YarnConfiguration();

    YarnClient yarnClient = YarnClient.createYarnClient();
    yarnClient.init(conf);
    yarnClient.start();

    YarnClientApplication app = yarnClient.createApplication();
    GetNewApplicationResponse appResponse = app.getNewApplicationResponse();

    // set the application submission context
    ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
    ApplicationId appId = appContext.getApplicationId();

    appContext.setKeepContainersAcrossApplicationAttempts(keepContainers);
    appContext.setApplicationName(appName);

    Map<String, LocalResource> localResources = new HashMap<>();

    FileSystem fs = FileSystem.get(conf);
    addToLocalResources(fs, appMasterJar, appMasterJarPath, appId.toString(), localResources, null);

    Map<String, String> env = new HashMap<>();

    StringBuilder classPathEnv = new StringBuilder(ApplicationConstants.Environment.CLASSPATH.$$())
        .append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");
    for (String c : conf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH,
        YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
      classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
      classPathEnv.append(c.trim());
    }

    logger.debug("classpath: {}", classPathEnv.toString());

    env.put("CLASSPATH", classPathEnv.toString());

    Vector<CharSequence> vargs = new Vector<>(30);

    vargs.add(ApplicationConstants.Environment.JAVA_HOME.$$() + "/bin/java");

    vargs.add("-Xmx128m");

    vargs.add(appMasterMainClass);

    vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/ApplicationMaster.stdout");
    vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/ApplicationMaster.stderr");
    String command = Joiner.on(" ").join(vargs);
    logger.info("command: {}", command);
    List<String> commands = Lists.newArrayList(command);

    ContainerLaunchContext amContainer =
        ContainerLaunchContext.newInstance(localResources, env, commands, null, null, null);

    Resource capability = Resource.newInstance(128, 1);
    appContext.setResource(capability);

    if (UserGroupInformation.isSecurityEnabled()) {
      Credentials credentials = new Credentials();
      String tokenRenewer = conf.get(YarnConfiguration.RM_PRINCIPAL);
      if (tokenRenewer == null || tokenRenewer.length() == 0) {
        throw new IOException("Can't get Master Kerberos prinipal for the RM to use as renewer");
      }

      final Token<?> tokens[] = fs.addDelegationTokens(tokenRenewer, credentials);
      if (tokens != null) {
        for (Token<?> token : tokens) {
          logger.info("Got dt from " + fs.getUri() + "; " + token);
        }
      }

      DataOutputBuffer dob = new DataOutputBuffer();
      credentials.writeTokenStorageToStream(dob);
      ByteBuffer fsTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());
      amContainer.setTokens(fsTokens);
    }

    appContext.setAMContainerSpec(amContainer);

    Priority pri = Priority.newInstance(0);
    appContext.setPriority(pri);

    appContext.setQueue("default");

    appContext.setApplicationType("ZEPPELIN");

    yarnClient.submitApplication(appContext);

    ApplicationReport report = yarnClient.getApplicationReport(appId);

    yarnClient.stop();
  }

  private void addToLocalResources(FileSystem fs, String fileSrcPath, String fileDstPath,
      String appId, Map<String, LocalResource> localResources, String resources)
      throws IOException {
    String suffix = appName + "/" + appId + "/" + fileDstPath;
    Path dst = new Path(fs.getHomeDirectory(), suffix);
    if (fileSrcPath == null) {
      FSDataOutputStream ostream = null;
      try {
        ostream = FileSystem.create(fs, dst, new FsPermission((short) 0710));
        ostream.writeUTF(resources);
      } finally {
        IOUtils.closeQuietly(ostream);
      }
    } else {
      fs.copyFromLocalFile(new Path(fileSrcPath), dst);
    }
    FileStatus scFileStatus = fs.getFileStatus(dst);
    LocalResource scRsrc = LocalResource
        .newInstance(ConverterUtils.getYarnUrlFromURI(dst.toUri()), LocalResourceType.FILE,
            LocalResourceVisibility.APPLICATION, scFileStatus.getLen(),
            scFileStatus.getModificationTime());
    localResources.put(fileDstPath, scRsrc);
  }
}
