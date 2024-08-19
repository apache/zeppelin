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
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class ClusterNoteEventListenerTest implements ClusterEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterNoteEventListenerTest.class);

  public String receiveMsg = null;

  private ZeppelinConfiguration conf;
  private NoteParser noteParser;

  @BeforeEach
  void setup() {
    conf = ZeppelinConfiguration.load();
    noteParser = new GsonNoteParser(conf);
  }

  @Override
  public void onClusterEvent(String msg) {
    receiveMsg = msg;
    LOGGER.debug("ClusterNoteEventListenerTest#onClusterEvent : {}", msg);
    ClusterMessage message = ClusterMessage.deserializeMessage(msg);

    Note note = null;
    Paragraph paragraph = null;
    Set<String> userAndRoles = null;
    Map<String, Paragraph> userParagraphMap = null;
    AuthenticationInfo authenticationInfo = null;
    for (Map.Entry<String, String> entry : message.getData().entrySet()) {
      String key = entry.getKey();
      String json = entry.getValue();
      if (key.equals("AuthenticationInfo")) {
        authenticationInfo = AuthenticationInfo.fromJson(json);
        LOGGER.debug(authenticationInfo.toJson());
      } else if (key.equals("Note")) {
        try {
          note = noteParser.fromJson(null, json);
          LOGGER.debug(note.toJson());
        } catch (IOException e) {
          LOGGER.warn("Fail to parse note json", e);
        }
      } else if (key.equals("Paragraph")) {
        paragraph = noteParser.fromJson(json);
        LOGGER.debug(paragraph.toJson());
      } else if (key.equals("Set<String>")) {
        Gson gson = new Gson();
        userAndRoles = gson.fromJson(json, new TypeToken<Set<String>>() {
        }.getType());
        LOGGER.debug(userAndRoles.toString());
      } else if (key.equals("Map<String, Paragraph>")) {
        Gson gson = new Gson();
        userParagraphMap = gson.fromJson(json, new TypeToken<Map<String, Paragraph>>() {
        }.getType());
        LOGGER.debug(userParagraphMap.toString());
      } else {
        receiveMsg = null;
        fail("Unknown clusterEvent : " + message.clusterEvent);
      }
    }
  }
}
