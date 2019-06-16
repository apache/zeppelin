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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.cluster.event.ClusterMessage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ClusterAuthEventListenerTest implements ClusterEventListener {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterAuthEventListenerTest.class);

  public String receiveMsg = null;

  @Override
  public void onClusterEvent(String msg) {
    receiveMsg = msg;
    LOGGER.info("onClusterEvent : {}", msg);
    ClusterMessage message = ClusterMessage.deserializeMessage(msg);

    String noteId = message.get("noteId");
    String user = message.get("user");
    String jsonSet = message.get("set");
    Gson gson = new Gson();
    Set<String> set  = gson.fromJson(jsonSet, new TypeToken<Set<String>>() {
    }.getType());

    assertNotNull(set);
    switch (message.clusterEvent) {
      case SET_READERS_PERMISSIONS:
      case SET_WRITERS_PERMISSIONS:
      case SET_OWNERS_PERMISSIONS:
      case SET_RUNNERS_PERMISSIONS:
      case CLEAR_PERMISSION:
        assertNotNull(noteId);
        break;
      case SET_ROLES:
        assertNotNull(user);
        break;
      default:
        receiveMsg = null;
        fail("Unknown clusterEvent : " + message.clusterEvent);
        break;
    }
  }
}
