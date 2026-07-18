/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.service.JobManagerService.NoteJobInfo;
import org.apache.zeppelin.service.exception.JobManagerForbiddenException;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class JobManagerServiceTest {

  private ZeppelinConfiguration zConf;
  private Notebook mockNotebook;
  private AuthorizationService mockAuthorizationService;
  private JobManagerService jobManagerService;
  private ServiceContext serviceContext;

  @BeforeEach
  public void setUp() {
    zConf = mock(ZeppelinConfiguration.class);
    mockNotebook = mock(Notebook.class);
    mockAuthorizationService = mock(AuthorizationService.class);
    jobManagerService = new JobManagerService(mockNotebook, mockAuthorizationService, zConf);
    serviceContext = new ServiceContext(new AuthenticationInfo("test-user"), null);
  }

  @Nested
  class WhenJobManagerIsDisabled {

    @BeforeEach
    void disableJobManager() {
      when(zConf.isJobManagerEnabled()).thenReturn(false);
    }

    @Test
    void checkIfJobManagerIsEnabled_throwsException() {
      assertThrows(JobManagerForbiddenException.class, () -> jobManagerService.checkIfJobManagerIsEnabled());
    }

    @Test
    void getNoteJobInfo_returnsEmptyList_andCallsCallback() throws IOException {
      @SuppressWarnings("unchecked")
      ServiceCallback<List<NoteJobInfo>> callback = mock(ServiceCallback.class);
      List<NoteJobInfo> result = jobManagerService.getNoteJobInfo(
          "some_note_id",
          serviceContext,
          callback
      );

      assertNotNull(result);
      assertTrue(result.isEmpty());

      verify(callback).onFailure(any(JobManagerForbiddenException.class), eq(serviceContext));
    }

    @Test
    void getNoteJobInfoByUnixTime_returnsEmptyList() throws IOException {
      ServiceCallback<List<NoteJobInfo>> callback = new SimpleServiceCallback<>();
      List<NoteJobInfo> result = jobManagerService.getNoteJobInfoByUnixTime(
          0,
          serviceContext,
          callback
      );

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void removeNoteJobInfo_doesNothing() {
      ServiceCallback<List<NoteJobInfo>> callback = new SimpleServiceCallback<>();
      assertDoesNotThrow(() ->
          jobManagerService.removeNoteJobInfo(
              "some_note_id",
              serviceContext,
              callback
          )
      );
    }
  }

  @Nested
  class WhenJobManagerIsEnabled {

    private static final long LAST_RUN_TIME = 200_000L;
    private static final long NEVER_RUN_CREATED_TIME = 100_000L;

    private Note mockNote;

    @BeforeEach
    void enableJobManager() throws IOException {
      when(zConf.isJobManagerEnabled()).thenReturn(true);

      mockNote = mock(Note.class);
      when(mockNote.getId()).thenReturn("note1");
      when(mockNote.getName()).thenReturn("note1");
      when(mockNote.getConfig()).thenReturn(Collections.emptyMap());
      when(mockNote.getDefaultInterpreterGroup()).thenReturn("spark");

      Paragraph lastRunParagraph = mock(Paragraph.class);
      when(lastRunParagraph.isTerminated()).thenReturn(true);
      when(lastRunParagraph.getDateFinished()).thenReturn(new Date(LAST_RUN_TIME));
      when(lastRunParagraph.getStatus()).thenReturn(Job.Status.FINISHED);
      when(lastRunParagraph.getId()).thenReturn("p1");
      when(lastRunParagraph.getTitle()).thenReturn(null);

      Paragraph neverRunParagraph = mock(Paragraph.class);
      when(neverRunParagraph.isTerminated()).thenReturn(false);
      when(neverRunParagraph.isRunning()).thenReturn(false);
      when(neverRunParagraph.getDateCreated()).thenReturn(new Date(NEVER_RUN_CREATED_TIME));
      when(neverRunParagraph.getStatus()).thenReturn(Job.Status.READY);
      when(neverRunParagraph.getId()).thenReturn("p2");
      when(neverRunParagraph.getTitle()).thenReturn(null);

      when(mockNote.getParagraphs()).thenReturn(List.of(lastRunParagraph, neverRunParagraph));

      when(mockNotebook.getNotesInfo()).thenReturn(List.of(new NoteInfo("note1", "note1.zpln")));
      when(mockAuthorizationService.isOwner(any(), eq("note1"))).thenReturn(true);
      when(mockNotebook.processNote(eq("note1"), any())).thenAnswer(invocation -> {
        Notebook.NoteProcessor<Object> noteProcessor = invocation.getArgument(1);
        return noteProcessor.process(mockNote);
      });
    }

    @Test
    void getNoteJobInfoByUnixTime_usesMaxParagraphTimestamp_notLastParagraph() throws IOException {
      ServiceCallback<List<NoteJobInfo>> callback = new SimpleServiceCallback<>();

      List<NoteJobInfo> result = jobManagerService.getNoteJobInfoByUnixTime(
          (NEVER_RUN_CREATED_TIME + LAST_RUN_TIME) / 2,
          serviceContext,
          callback
      );

      assertEquals(1, result.size());
    }

    @Test
    void getNoteJobInfoByUnixTime_boundaryIsExactlyMaxTimestamp() throws IOException {
      ServiceCallback<List<NoteJobInfo>> callback = new SimpleServiceCallback<>();

      List<NoteJobInfo> includedResult = jobManagerService.getNoteJobInfoByUnixTime(
          LAST_RUN_TIME - 1,
          serviceContext,
          callback
      );
      List<NoteJobInfo> excludedResult = jobManagerService.getNoteJobInfoByUnixTime(
          LAST_RUN_TIME,
          serviceContext,
          callback
      );

      assertEquals(1, includedResult.size());
      assertTrue(excludedResult.isEmpty());
    }
  }

}
