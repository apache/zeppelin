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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.recovery.FileSystemRecoveryStorage;
import org.apache.zeppelin.interpreter.recovery.StopInterpreter;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

class RecoveryTest extends AbstractTestRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryTest.class);
  private Gson gson = new Gson();
  private static File recoveryDir = null;
  private static MiniZeppelinServer zepServer;

  private Notebook notebook;

  private AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(RecoveryTest.class.getSimpleName());
    zepServer.addInterpreter("sh");
    zepServer.addInterpreter("python");
    zepServer.copyLogProperties();
    zepServer.copyBinDir();
    zepServer.getZeppelinConfiguration().setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getVarName(),
        FileSystemRecoveryStorage.class.getName());
    recoveryDir = Files.createTempDirectory("recovery").toFile();
    zepServer.getZeppelinConfiguration().setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_DIR.getVarName(),
        recoveryDir.getAbsolutePath());
    zepServer.start();

  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
    FileUtils.deleteDirectory(recoveryDir);
  }

  @BeforeEach
  void setUp() {
    zConf = zepServer.getZeppelinConfiguration();
    notebook = zepServer.getService(Notebook.class);
    anonymous = new AuthenticationInfo("anonymous");
  }


  @Test
  void testRecovery() throws Exception {
    LOGGER.info("Test testRecovery");
    String note1Id = null;
    try {
      note1Id = notebook.createNote("note1", anonymous);
      notebook.processNote(note1Id,
        note1 -> {
          // run python interpreter and create new variable `user`
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python user='abc'");
          return null;
        });

      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id +"?blocking=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          notebook.saveNote(note1, anonymous);
          return null;
        });

      // shutdown zeppelin and restart it
      zepServer.shutDown(false);
      zepServer.start();

      // run the paragraph again, but change the text to print variable `user`
      Thread.sleep(10 * 1000);
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          p1 = note1.getParagraph(p1.getId());
          p1.setText("%python print(user)");
          return null;
        });
      post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertEquals("OK", resp.get("status"));
      post.close();
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals("abc\n", p1.getReturn().message().get(0).getData());
          return null;
        });
    } catch (Exception e) {
      LOGGER.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  void testRecovery_2() throws Exception {
    LOGGER.info("Test testRecovery_2");
    String note1Id = null;
    try {
      note1Id = notebook.createNote("note2", AuthenticationInfo.ANONYMOUS);

      // run python interpreter and create new variable `user`
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python user='abc'");
          return null;
        });
      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          notebook.saveNote(note1, AuthenticationInfo.ANONYMOUS);
          // restart the python interpreter
          try {
            notebook.getInterpreterSettingManager().restart(
                ((ManagedInterpreterGroup) p1.getBindedInterpreter().getInterpreterGroup())
                    .getInterpreterSetting().getId()
            );
          } catch (InterpreterException e) {
            fail(e);
          }
          return null;
        });

      // shutdown zeppelin and restart it
      zepServer.shutDown();
      zepServer.start();

      Thread.sleep(5 * 1000);
      // run the paragraph again, but change the text to print variable `user`.
      // can not recover the python interpreter, because it has been shutdown.
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          p1.setText("%python print(user)");
          return null;
        });
      post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertEquals("OK", resp.get("status"));
      post.close();
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.ERROR, p1.getStatus());
          return null;
        });
    } catch (Exception e) {
      LOGGER.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  void testRecovery_3() throws Exception {
    LOGGER.info("Test testRecovery_3");
    String note1Id = null;
    try {
      note1Id = notebook.createNote("note3", AuthenticationInfo.ANONYMOUS);
      notebook.processNote(note1Id,
        note1 -> {
          // run python interpreter and create new variable `user`
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python user='abc'");
          return null;
        });

      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          notebook.saveNote(note1, AuthenticationInfo.ANONYMOUS);
          return null;
        });

      // shutdown zeppelin and restart it
      zepServer.shutDown();
      new StopInterpreter(zConf);
      zepServer.start();
      Thread.sleep(5 * 1000);
      // run the paragraph again, but change the text to print variable `user`.
      // can not recover the python interpreter, because it has been shutdown.
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          p1.setText("%python print(user)");
          return null;
        });

      post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertEquals("OK", resp.get("status"));
      post.close();
      notebook.processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.ERROR, p1.getStatus());
          return null;
        });
    } catch (Exception e ) {
      LOGGER.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  private Callable<Boolean> hasParagraphStatus(Paragraph p, Job.Status status) {
    return () -> p.getStatus().equals(status);
  }

  private Callable<Boolean> isParagraphTerminated(Paragraph p) {
    return () -> p.isTerminated();
  }

  @Test
  void testRecovery_Running_Paragraph_sh() throws Exception {
    LOGGER.info("Test testRecovery_Running_Paragraph_sh");
    String note1Id = null;
    try {
      note1Id = notebook.createNote("note4",
          AuthenticationInfo.ANONYMOUS);
      Paragraph p1 = notebook.processNote(note1Id,
        note1 -> {
          return note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        });
      p1.setText("%sh sleep 10\necho 'hello'");
      // run sh paragraph async, print 'hello' after 10 seconds
      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "/" + p1.getId(), "");
      assertThat(post, isAllowed());
      post.close();
      // wait until paragraph is RUNNING
      await().until(hasParagraphStatus(p1, Job.Status.RUNNING));
      if (p1.getStatus() != Job.Status.RUNNING) {
        fail("Fail to run paragraph: " + p1.getReturn());
      }

      // shutdown zeppelin and restart it
      zepServer.shutDown();
      zepServer.start();

      // wait until paragraph is finished
      await().until(isParagraphTerminated(p1));
      // Wait because paragraph is re submited
      Thread.sleep(11 * 1000);
      assertEquals(Job.Status.FINISHED, p1.getStatus());
      assertEquals("hello\n", p1.getReturn().message().get(0).getData());
      Thread.sleep(5 * 1000);
    } catch (Exception e ) {
      LOGGER.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  void testRecovery_Finished_Paragraph_python() throws Exception {
    LOGGER.info("Test testRecovery_Finished_Paragraph_python");
    String note1Id = null;
    try {
      InterpreterSettingManager interpreterSettingManager =
          zepServer.getService(InterpreterSettingManager.class);
      InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
      interpreterSetting.setProperty("zeppelin.python.useIPython", "false");
      interpreterSetting.setProperty("zeppelin.interpreter.result.cache", "100");

      note1Id = notebook.createNote("note4", AuthenticationInfo.ANONYMOUS);

      // run  paragraph async, print 'hello' after 10 seconds
      Paragraph p1 = notebook.processNote(note1Id,
        note1 -> {
          return note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        });
      p1.setText("%python import time\n" +
              "for i in range(1, 10):\n" +
              "    time.sleep(1)\n" +
              "    print(i)");
      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "/" + p1.getId(), "");
      assertThat(post, isAllowed());
      post.close();

      // wait until paragraph is running
      while(p1.getStatus() != Job.Status.RUNNING) {
        Thread.sleep(1000);
      }

      // shutdown zeppelin and restart it
      zepServer.shutDown();
      // sleep 15 seconds to make sure the paragraph is finished
      Thread.sleep(15 * 1000);

      zepServer.start();
      // sleep 10 seconds to make sure recovering is finished
      Thread.sleep(10 * 1000);

      assertEquals(Job.Status.FINISHED, p1.getStatus());
      assertEquals("1\n" +
              "2\n" +
              "3\n" +
              "4\n" +
              "5\n" +
              "6\n" +
              "7\n" +
              "8\n" +
              "9\n", p1.getReturn().message().get(0).getData());
    } catch (Exception e ) {
      LOGGER.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }
}
