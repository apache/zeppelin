/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.cluster.yarn;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by jl on 04/07/2017.
 */
public class YarnClientTest {
  private static MiniYARNCluster miniYARNCluster;
  private static YarnConfiguration yarnConfiguration;

  @BeforeClass
  public static void setupCluster() {
    yarnConfiguration = new YarnConfiguration();
    yarnConfiguration.set("yarn.nodemanager.disk-health-checker" +
        ".max-disk-utilization-per-disk-percentage", "100.0");
    yarnConfiguration.set(YarnConfiguration.YARN_MINICLUSTER_FIXED_PORTS, "true");
    yarnConfiguration.set(YarnConfiguration.YARN_MINICLUSTER_USE_RPC, "true");

    miniYARNCluster = new MiniYARNCluster(YarnClientTest.class.getName(), 1, 1, 1);
    miniYARNCluster.init(yarnConfiguration);
    miniYARNCluster.start();

    final CountDownLatch waitUntilLaunched = new CountDownLatch(1);
    final AtomicInteger checkerRunning = new AtomicInteger(0);

    new Thread(new Runnable() {
      @Override
      public void run() {
        while (0 < checkerRunning.get()) {
          if (!"0".equals(miniYARNCluster.getConfig().get(YarnConfiguration.RM_ADDRESS).split(":")[1])) {
            waitUntilLaunched.countDown();
          }
        }
      }
    }).start();

    try {
      waitUntilLaunched.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      // Do nothing
    }

    if ("0".equals(miniYARNCluster.getConfig().get(YarnConfiguration.RM_ADDRESS).split(":")[1])) {
      throw new RuntimeException("Cannot run MiniYarnCluster for testing yarn");
    }
  }

  @AfterClass
  public static void shutdownCluster() {
    try {
      miniYARNCluster.close();
    } catch (IOException e) {
      // Do nothing
    }
  }

  @Test
  public void clientTest() throws Exception {
    System.out.println("RM_ADDRESS = " + miniYARNCluster.getConfig().get(YarnConfiguration.RM_ADDRESS));
    Client client = new Client(miniYARNCluster.getConfig());
    client.start();
    RemoteInterpreterProcess remoteInterpreterProcess = client.createInterpreter("id", "name", "fake", Maps.<String, String>newHashMap(), new Properties(), 100000, null, null, "fakeHome", "fake");
    String parentClasspath = Paths.get(YarnRemoteInterpreterServer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent().getParent().toAbsolutePath().toString();
    String commonClasspath = parentClasspath + "/common/target";
    String yarnClasspath = parentClasspath + "/yarn/target";
    System.out.println("parentClasspath: " + parentClasspath);
    System.out.println("commonClasspath: " + commonClasspath);
    System.out.println("yarnClasspath: " + yarnClasspath);
    String interpreterClasspath = Paths.get(parentClasspath).getParent().toAbsolutePath().toString() + "/zeppelin-interpreter/target";
    ((RemoteInterpreterYarnProcess)remoteInterpreterProcess).setExtraClasspath(commonClasspath + "/classes" + ApplicationConstants.CLASS_PATH_SEPARATOR + commonClasspath + "/lib/*" + ApplicationConstants.CLASS_PATH_SEPARATOR + yarnClasspath + "/classes" + ApplicationConstants.CLASS_PATH_SEPARATOR + yarnClasspath + "/lib/*" + ApplicationConstants.CLASS_PATH_SEPARATOR + interpreterClasspath+ "/classes" + ApplicationConstants.CLASS_PATH_SEPARATOR + interpreterClasspath + "/lib/*");
    remoteInterpreterProcess.start("user", false);
    assertNotNull("Host should not be null", remoteInterpreterProcess.getHost());
    assertFalse("Port should not be -1", -1 == remoteInterpreterProcess.getPort());
  }
}