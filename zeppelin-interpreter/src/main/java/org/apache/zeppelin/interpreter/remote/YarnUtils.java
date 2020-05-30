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

package org.apache.zeppelin.interpreter.remote;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Singleton class which is used for register/unregister yarn app AM.
 * The reason we put it in a separated class instead of putting it into RemoteInterpreterServer is that
 * this class is only needed when launching interpreter process in yarn mode. If we put it into RemoteInterpreterServer
 * We would get ClassNotFoundException when we don't launch interpreter process in yarn mode
 */
public class YarnUtils {

  private static Logger LOGGER = LoggerFactory.getLogger(YarnUtils.class);

  private static AMRMClient amClient = AMRMClient.createAMRMClient();
  private static Configuration conf = new YarnConfiguration();

  static {
    amClient.init(conf);
    amClient.start();
  }

  public static void register(String host, int port) throws Exception {
    LOGGER.info("Registering yarn app at " + host + ":" + port);
    try {
      amClient.registerApplicationMaster(host, port, null);
    } catch (YarnException e) {
      throw new Exception("Fail to register yarn app", e);
    }
  }

  public static void heartbeat() {
    LOGGER.info("Heartbeating to RM");
    try {
      amClient.allocate(1.0f);
    } catch (Exception e) {
      LOGGER.warn(e.getMessage(), e);
    }
  }

  public static void unregister(boolean succcess, String diagnostic) throws Exception {
    LOGGER.info("Unregistering yarn app");
    try {
      if (succcess) {
        amClient.unregisterApplicationMaster(FinalApplicationStatus.SUCCEEDED, "", null);
      } else {
        amClient.unregisterApplicationMaster(FinalApplicationStatus.FAILED, diagnostic, null);
      }
    } catch (YarnException e) {
        throw new Exception("Fail to unregister yarn app", e);
    }
  }
}
