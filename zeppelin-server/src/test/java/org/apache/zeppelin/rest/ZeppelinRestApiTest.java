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

package org.apache.zeppelin.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
/**
 * BASIC Zeppelin rest api tests
 * TODO: Add Post,Put,Delete test and method
 *
 * @author anthonycorbacho
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ZeppelinRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  /***
   * ROOT API TEST
   ***/
  @Test
  public void getApiRoot() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }


  @Test
  public void getAvailableInterpreters() throws IOException {
    // when
    GetMethod get = httpGet("/interpreter");

    // then
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    assertEquals(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETERS.getStringValue().split(",").length, body.size());
    get.releaseConnection();
  }

  @Test
  public void getSettings() throws IOException {
    // when
    GetMethod get = httpGet("/interpreter/setting");

    // then
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>(){}.getType());
    assertThat(get, isAllowed());
    get.releaseConnection();
  }


  @Test
  public void testInterpreterAutoBinding() throws IOException {
    // create note
    Note note = ZeppelinServer.notebook.createNote();

    // check interpreter is bindded
    GetMethod get = httpGet("/notebook/interpreter/bind/"+note.id());
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>(){}.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    assertTrue(0 < body.size());

    get.releaseConnection();
  }

  @Test
  public void testInterpreterRestart() throws IOException, InterruptedException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();
    note.addParagraph();
    Paragraph p = note.getLastParagraph();

    // run markdown paragraph
    p.setText("%md markdown");
    note.run(p.getId());
    while (p.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }
    assertEquals("<p>markdown</p>\n", p.getResult().message());

    // restart interpreter
    for (InterpreterSetting setting : note.getNoteReplLoader().getInterpreterSettings()) {
      if (setting.getName().equals("md")) {
        // restart
        ZeppelinServer.notebook.getInterpreterFactory().restart(setting.id());
        break;
      }
    }

    // run markdown paragraph, again
    p = note.addParagraph();
    p.setText("%md markdown restarted");
    note.run(p.getId());
    while (p.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }
    assertEquals("<p>markdown restarted</p>\n", p.getResult().message());
  }
}
