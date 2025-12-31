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

import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

class ShellIntegrationTest extends AbstractTestRestApi {

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(ShellIntegrationTest.class.getSimpleName());
    zepServer.addInterpreter("sh");
    zepServer.copyBinDir();
    zepServer.getZeppelinConfiguration().setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
        "helium");
    zepServer.start();
  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setup() {
    zConf = zepServer.getZeppelinConfiguration();
  }

  @Test
  void testBasicShell() throws IOException {
    String noteId = null;
    try {
      noteId = zepServer.getService(Notebook.class).createNote("note1", AuthenticationInfo.ANONYMOUS);
      zepServer.getService(Notebook.class).processNote(noteId,
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
        zepServer.getService(Notebook.class).removeNote(noteId, AuthenticationInfo.ANONYMOUS);
      }
    }
  }
}
