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

import java.io.IOException;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedCronRestApiTest extends AbstractTestRestApi {
  private static final String ADMIN_USER = "admin";
  private static final String ADMIN_PASSWORD = "password1";
  private static final String VALID_CRON_REQUEST = "{\"cron\":\"0 0 0 1 1 ? 2099\"}";

  private static MiniZeppelinServer zepServer;
  private Notebook notebook;
  private AuthenticationInfo admin;

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(AuthenticatedCronRestApiTest.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", ZEPPELIN_SHIRO);
    zepServer.addInterpreter("md");
    zepServer.getZeppelinConfiguration().setProperty(
        ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
    zepServer.getZeppelinConfiguration().setProperty(
        ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName(), "/System");
    zepServer.start();
  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setUp() {
    zConf = zepServer.getZeppelinConfiguration();
    notebook = zepServer.getService(Notebook.class);
    admin = new AuthenticationInfo(ADMIN_USER);
  }

  @Test
  void testCronForNonexistentNote() throws IOException {
    try (
        CloseableHttpResponse response =
            httpPost(
                "/notebook/cron/notexistnote",
                VALID_CRON_REQUEST,
                ADMIN_USER,
                ADMIN_PASSWORD)) {
      assertThat("", response, isNotFound());
    }
  }

  @Test
  void testCronLifecycleInConfiguredFolder() throws Exception {
    String noteId = null;
    try {
      assertTrue(zConf.isAuthenticationEnabled());
      assertTrue(zConf.isZeppelinNotebookCronEnable());
      noteId = notebook.createNote("/System/testCronLifecycleInConfiguredFolder", admin);
      notebook.processNote(noteId,
          note -> {
            assertNotNull(note, "can't create new note");
            note.setName("testCronLifecycleInConfiguredFolder");
            Paragraph paragraph = note.addNewParagraph(admin);
            Map<String, Object> config = paragraph.getConfig();
            config.put("enabled", true);
            paragraph.setConfig(config);
            paragraph.setText("%md This is test paragraph.");
            notebook.saveNote(note, admin);
            return null;
          });

      try (
          CloseableHttpResponse response =
              httpPost(
                  "/notebook/cron/" + noteId,
                  VALID_CRON_REQUEST,
                  ADMIN_USER,
                  ADMIN_PASSWORD)) {
        assertThat("", response, isAllowed());
      }

      try (
          CloseableHttpResponse response =
              httpGet(
                  "/notebook/cron/" + noteId,
                  ADMIN_USER,
                  ADMIN_PASSWORD)) {
        assertThat("", response, isAllowed());
      }

      String invalidCronRequest = "{\"cron\":\"a * * * * ?\"}";
      try (
          CloseableHttpResponse response =
              httpPost(
                  "/notebook/cron/" + noteId,
                  invalidCronRequest,
                  ADMIN_USER,
                  ADMIN_PASSWORD)) {
        assertThat("", response, isBadRequest());
      }

      try (
          CloseableHttpResponse response =
              httpDelete(
                  "/notebook/cron/" + noteId,
                  ADMIN_USER,
                  ADMIN_PASSWORD)) {
        assertThat("", response, isAllowed());
      }
    } finally {
      if (noteId != null) {
        notebook.removeNote(noteId, admin);
      }
    }
  }

  @Test
  void testCronRejectedOutsideConfiguredFolder() throws Exception {
    String noteId = null;
    try {
      noteId = notebook.createNote("/Other/testCronRejectedOutsideConfiguredFolder", admin);
      notebook.processNote(noteId,
          note -> {
            assertNotNull(note, "can't create new note");
            note.setName("testCronRejectedOutsideConfiguredFolder");
            Paragraph paragraph = note.addNewParagraph(admin);
            Map<String, Object> config = paragraph.getConfig();
            config.put("enabled", true);
            paragraph.setConfig(config);
            paragraph.setText("%md This is test paragraph.");
            notebook.saveNote(note, admin);
            return null;
          });

      try (
          CloseableHttpResponse response =
              httpPost(
                  "/notebook/cron/" + noteId,
                  VALID_CRON_REQUEST,
                  ADMIN_USER,
                  ADMIN_PASSWORD)) {
        assertThat("", response, isForbidden());
      }
    } finally {
      if (noteId != null) {
        notebook.removeNote(noteId, admin);
      }
    }
  }
}
