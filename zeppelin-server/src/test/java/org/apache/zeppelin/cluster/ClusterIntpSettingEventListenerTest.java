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
package org.apache.zeppelin.cluster;

import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.cluster.event.ClusterMessage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

public class ClusterIntpSettingEventListenerTest implements ClusterEventListener {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterIntpSettingEventListenerTest.class);

  public String receiveMsg = null;

  @Override
  public void onClusterEvent(String msg) {
    receiveMsg = msg;
    LOGGER.info("ClusterIntpSettingEventListenerTest#onClusterEvent : {}", msg);
    ClusterMessage message = ClusterMessage.deserializeMessage(msg);
  }
}
