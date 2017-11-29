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

package org.apache.zeppelin.recovery;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.recovery.FileSystemRecoveryStorage;
import org.apache.zeppelin.interpreter.recovery.StopInterpreter;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RecoveryTest extends AbstractTestRestApi {

  private Gson gson = new Gson();
  private static File recoveryDir = null;

  @BeforeClass
  public static void init() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getVarName(),
        FileSystemRecoveryStorage.class.getName());
    recoveryDir = Files.createTempDir();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_DIR.getVarName(), recoveryDir.getAbsolutePath());
    startUp(RecoveryTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    shutDown();
    FileUtils.deleteDirectory(recoveryDir);
  }

  @Test
  public void testRecovery() throws Exception {
    Note note1 = ZeppelinServer.notebook.createNote(AuthenticationInfo.ANONYMOUS);

    // run python interpreter and create new variable `user`
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%python user='abc'");
    PostMethod post = httpPost("/notebook/job/" + note1.getId(), "");
    assertThat(post, isAllowed());
    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    assertEquals(resp.get("status"), "OK");
    post.releaseConnection();
    assertEquals(Job.Status.FINISHED, p1.getStatus());

    // shutdown zeppelin and restart it
    shutDown();
    startUp(RecoveryTest.class.getSimpleName());

    // run the paragraph again, but change the text to print variable `user`
    note1 = ZeppelinServer.notebook.getNote(note1.getId());
    p1 = note1.getParagraph(p1.getId());
    p1.setText("%python print(user)");
    post = httpPost("/notebook/job/" + note1.getId(), "");
    assertEquals(resp.get("status"), "OK");
    post.releaseConnection();
    assertEquals(Job.Status.FINISHED, p1.getStatus());
    assertEquals("abc\n", p1.getResult().message().get(0).getData());
  }

  @Test
  public void testRecovery_2() throws Exception {
    Note note1 = ZeppelinServer.notebook.createNote(AuthenticationInfo.ANONYMOUS);

    // run python interpreter and create new variable `user`
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%python user='abc'");
    PostMethod post = httpPost("/notebook/job/" + note1.getId(), "");
    assertThat(post, isAllowed());
    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    assertEquals(resp.get("status"), "OK");
    post.releaseConnection();
    assertEquals(Job.Status.FINISHED, p1.getStatus());

    // restart the python interpreter
    ZeppelinServer.notebook.getInterpreterSettingManager().restart(
        ((ManagedInterpreterGroup) p1.getBindedInterpreter().getInterpreterGroup())
            .getInterpreterSetting().getId()
    );

    // shutdown zeppelin and restart it
    shutDown();
    startUp(RecoveryTest.class.getSimpleName());

    // run the paragraph again, but change the text to print variable `user`.
    // can not recover the python interpreter, because it has been shutdown.
    note1 = ZeppelinServer.notebook.getNote(note1.getId());
    p1 = note1.getParagraph(p1.getId());
    p1.setText("%python print(user)");
    post = httpPost("/notebook/job/" + note1.getId(), "");
    assertEquals(resp.get("status"), "OK");
    post.releaseConnection();
    assertEquals(Job.Status.ERROR, p1.getStatus());
  }

  @Test
  public void testRecovery_3() throws Exception {
    Note note1 = ZeppelinServer.notebook.createNote(AuthenticationInfo.ANONYMOUS);

    // run python interpreter and create new variable `user`
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%python user='abc'");
    PostMethod post = httpPost("/notebook/job/" + note1.getId(), "");
    assertThat(post, isAllowed());
    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    assertEquals(resp.get("status"), "OK");
    post.releaseConnection();
    assertEquals(Job.Status.FINISHED, p1.getStatus());

    // shutdown zeppelin and restart it
    shutDown();
    StopInterpreter.main(new String[]{});

    startUp(RecoveryTest.class.getSimpleName());

    // run the paragraph again, but change the text to print variable `user`.
    // can not recover the python interpreter, because it has been shutdown.
    note1 = ZeppelinServer.notebook.getNote(note1.getId());
    p1 = note1.getParagraph(p1.getId());
    p1.setText("%python print(user)");
    post = httpPost("/notebook/job/" + note1.getId(), "");
    assertEquals(resp.get("status"), "OK");
    post.releaseConnection();
    assertEquals(Job.Status.ERROR, p1.getStatus());
  }
}
