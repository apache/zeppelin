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
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class RecoveryTest extends AbstractTestRestApi {

  private Gson gson = new Gson();
  private static File recoveryDir = null;

  private Notebook notebook;

  private AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");

  @Before
  public void init() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getVarName(),
            FileSystemRecoveryStorage.class.getName());
    recoveryDir = Files.createTempDirectory("recovery").toFile();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_DIR.getVarName(),
            recoveryDir.getAbsolutePath());
    startUp(RecoveryTest.class.getSimpleName());

    notebook = ServiceLocatorFactory.getInstance().find(ZeppelinServer.SERVICE_LOCATOR_NAME).getService(Notebook.class);
  }

  @After
  public void destroy() throws Exception {
    shutDown(true, true);
    FileUtils.deleteDirectory(recoveryDir);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getVarName(),
            ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS.getStringValue());
  }

  @Test
  public void testRecovery() throws Exception {
    LOG.info("Test testRecovery");
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
          TestUtils.getInstance(Notebook.class).saveNote(note1, anonymous);
          return null;
        });

      // shutdown zeppelin and restart it
      shutDown();
      startUp(RecoveryTest.class.getSimpleName(), false);

      // run the paragraph again, but change the text to print variable `user`
      Thread.sleep(10 * 1000);
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          p1 = note1.getParagraph(p1.getId());
          p1.setText("%python print(user)");
          return null;
        });
      post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertEquals("OK", resp.get("status"));
      post.close();
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals("abc\n", p1.getReturn().message().get(0).getData());
          return null;
        });
    } catch (Exception e) {
      LOG.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRecovery_2() throws Exception {
    LOG.info("Test testRecovery_2");
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
          TestUtils.getInstance(Notebook.class).saveNote(note1, AuthenticationInfo.ANONYMOUS);
          // restart the python interpreter
          try {
            TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().restart(
                ((ManagedInterpreterGroup) p1.getBindedInterpreter().getInterpreterGroup())
                    .getInterpreterSetting().getId()
            );
          } catch (InterpreterException e) {
            fail();
          }
          return null;
        });

      // shutdown zeppelin and restart it
      shutDown();
      startUp(RecoveryTest.class.getSimpleName(), false);

      Thread.sleep(5 * 1000);
      // run the paragraph again, but change the text to print variable `user`.
      // can not recover the python interpreter, because it has been shutdown.
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          p1.setText("%python print(user)");
          return null;
        });
      post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertEquals("OK", resp.get("status"));
      post.close();
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.ERROR, p1.getStatus());
          return null;
        });
    } catch (Exception e) {
      LOG.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRecovery_3() throws Exception {
    LOG.info("Test testRecovery_3");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note3", AuthenticationInfo.ANONYMOUS);
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
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
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          TestUtils.getInstance(Notebook.class).saveNote(note1, AuthenticationInfo.ANONYMOUS);
          return null;
        });

      // shutdown zeppelin and restart it
      shutDown();
      StopInterpreter.main(new String[]{});

      startUp(RecoveryTest.class.getSimpleName(), false);

      Thread.sleep(5 * 1000);
      // run the paragraph again, but change the text to print variable `user`.
      // can not recover the python interpreter, because it has been shutdown.
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          p1.setText("%python print(user)");
          return null;
        });

      post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertEquals("OK", resp.get("status"));
      post.close();
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          assertEquals(Job.Status.ERROR, p1.getStatus());
          return null;
        });
    } catch (Exception e ) {
      LOG.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRecovery_Running_Paragraph_sh() throws Exception {
    LOG.info("Test testRecovery_Running_Paragraph_sh");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note4", AuthenticationInfo.ANONYMOUS);
      Paragraph p1 = TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          return note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        });
      p1.setText("%sh sleep 10\necho 'hello'");
      // run sh paragraph async, print 'hello' after 10 seconds
      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "/" + p1.getId(), "");
      assertThat(post, isAllowed());
      post.close();
      long start = System.currentTimeMillis();
      // wait until paragraph is RUNNING
      while((System.currentTimeMillis() - start) < 10 * 1000) {
        if (p1.getStatus() == Job.Status.RUNNING) {
          break;
        }
        Thread.sleep(1000);
      }
      if (p1.getStatus() != Job.Status.RUNNING) {
        fail("Fail to run paragraph: " + p1.getReturn());
      }

      // shutdown zeppelin and restart it
      shutDown();
      startUp(RecoveryTest.class.getSimpleName(), false);

      // wait until paragraph is finished
      start = System.currentTimeMillis();
      while((System.currentTimeMillis() - start) < 10 * 1000) {
        if (p1.isTerminated()) {
          break;
        }
        Thread.sleep(1000);
      }

      assertEquals(Job.Status.FINISHED, p1.getStatus());
      assertEquals("hello\n", p1.getReturn().message().get(0).getData());
      Thread.sleep(5 * 1000);
    } catch (Exception e ) {
      LOG.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRecovery_Finished_Paragraph_python() throws Exception {
    LOG.info("Test testRecovery_Finished_Paragraph_python");
    String note1Id = null;
    try {
      InterpreterSettingManager interpreterSettingManager = TestUtils.getInstance(InterpreterSettingManager.class);
      InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
      interpreterSetting.setProperty("zeppelin.python.useIPython", "false");
      interpreterSetting.setProperty("zeppelin.interpreter.result.cache", "100");

      note1Id = TestUtils.getInstance(Notebook.class).createNote("note4", AuthenticationInfo.ANONYMOUS);

      // run  paragraph async, print 'hello' after 10 seconds
      Paragraph p1 = TestUtils.getInstance(Notebook.class).processNote(note1Id,
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
      shutDown();
      // sleep 15 seconds to make sure the paragraph is finished
      Thread.sleep(15 * 1000);

      startUp(RecoveryTest.class.getSimpleName(), false);
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
      LOG.error(e.toString(), e);
      throw e;
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }
}
