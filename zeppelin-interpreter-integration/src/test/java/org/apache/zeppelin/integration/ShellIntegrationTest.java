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

package org.apache.zeppelin.integration;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShellIntegrationTest extends AbstractTestRestApi {


  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
            "helium");
    AbstractTestRestApi.startUp(ShellIntegrationTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testBasicShell() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", AuthenticationInfo.ANONYMOUS);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

          // test correct shell command
          p.setText("%sh echo 'hello world'");
          note.run(p.getId(), true);
          assertEquals(Job.Status.FINISHED, p.getStatus());
          assertEquals("hello world\n", p.getReturn().message().get(0).getData());

          // test invalid shell command
          p.setText("%sh invalid_cmd");
          note.run(p.getId(), true);
          assertEquals(Job.Status.ERROR, p.getStatus());
          assertTrue(p.getReturn().toString(),
                  p.getReturn().message().get(0).getData().contains("command not found"));

          // test shell environment variable
          p.setText("%sh a='hello world'\n" +
                  "echo ${a}");
          note.run(p.getId(), true);
          assertEquals(Job.Status.FINISHED, p.getStatus());
          assertEquals("hello world\n", p.getReturn().message().get(0).getData());

          // use dynamic form via local property
          p.setText("%sh(form=simple) a='hello world'\n" +
                  "echo ${a}");
          note.run(p.getId(), true);
          assertEquals(Job.Status.FINISHED, p.getStatus());
          assertEquals(0, p.getReturn().message().size());
          return null;
        });

    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, AuthenticationInfo.ANONYMOUS);
      }
    }
  }
}
