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

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;

import static org.apache.hadoop.yarn.api.records.FinalApplicationStatus.SUCCEEDED;

/**
 *
 */
public class YarnRemoteInterpreterServer extends RemoteInterpreterServer {
  private static final Logger logger = LoggerFactory.getLogger(YarnRemoteInterpreterServer.class);

  private Configuration configuration;
  private AMRMClientAsync amrmClientAsync;

  public YarnRemoteInterpreterServer(int port) throws TTransportException, IOException {
    super(null, port);
    configuration = new Configuration();
  }

  public static void main(String[] args) throws Exception {
    int port = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    YarnRemoteInterpreterServer yarnRemoteInterpreterServer = new YarnRemoteInterpreterServer(port);
    yarnRemoteInterpreterServer.start();

    yarnRemoteInterpreterServer.join();
    System.exit(0);
  }

  @Override
  public void run() {
    logger.info("Start AMRMClient");
    amrmClientAsync = AMRMClientAsync.createAMRMClientAsync(1000, new AMRMCallbackHandler());
    amrmClientAsync.init(configuration);
    amrmClientAsync.start();

    try {
      String host = InetAddress.getLocalHost().getHostName();
      logger.info("Host: {}, port: {}", host, getPort());
      amrmClientAsync
          .registerApplicationMaster(host, getPort(), "http://localhost:8080");
    } catch (YarnException | IOException e) {
      logger.error("Error on registering application. Will shutdown");
      try {
        shutdown();
      } catch (TException e1) {
        logger.error("Never reach at", e1);
      }
    }

    super.run();
  }

  @Override
  public void shutdown() throws TException {
    try {
      amrmClientAsync.unregisterApplicationMaster(SUCCEEDED, "Shutdown successfully", null);
    } catch (IOException | YarnException e) {
      logger.error("Error while unregistering application", e);
    }
    super.shutdown();

  }

  private class AMRMCallbackHandler implements AMRMClientAsync.CallbackHandler {
    @Override
    public void onContainersCompleted(List<ContainerStatus> list) {
      logger.error("Never called");
    }

    @Override
    public void onContainersAllocated(List<Container> list) {
      logger.error("Never called");
    }

    @Override
    public void onShutdownRequest() {
      try {
        shutdown();
      } catch (TException e) {
        logger.error("Error while shutting down", e);
      }
    }

    @Override
    public void onNodesUpdated(List<NodeReport> list) {
      logger.debug("onNoodesUpdated. {}", list);
    }

    @Override
    public float getProgress() {
      return 0.5f;
    }

    @Override
    public void onError(Throwable throwable) {
      logger.error("Error on Application", throwable);
    }
  }
}
