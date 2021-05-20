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

package org.apache.zeppelin.service;

import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for JobManager Page
 */
public class JobManagerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobManagerService.class);

  private final Notebook notebook;
  private final AuthorizationService authorizationService;
  private final ZeppelinConfiguration conf;

  @Inject
  public JobManagerService(Notebook notebook,
                           AuthorizationService authorizationService,
                           ZeppelinConfiguration conf) {
    this.notebook = notebook;
    this.authorizationService = authorizationService;
    this.conf = conf;
  }

  public List<NoteJobInfo> getNoteJobInfo(String noteId,
                                          ServiceContext context,
                                          ServiceCallback<List<NoteJobInfo>> callback)
      throws IOException {
    if (!conf.isJobManagerEnabled()) {
      return new ArrayList<>();
    }
    List<NoteJobInfo> notesJobInfo = new ArrayList<>();
    Note jobNote = notebook.getNote(noteId);
    if (jobNote == null) {
      callback.onFailure(new IOException("Note " + noteId + " not found"), context);
    } else {
      notesJobInfo.add(new NoteJobInfo(jobNote));
      callback.onSuccess(notesJobInfo, context);
    }
    return notesJobInfo;
  }

  /**
   * Get all NoteJobInfo after lastUpdateServerUnixTime
   */
  public List<NoteJobInfo> getNoteJobInfoByUnixTime(long lastUpdateServerUnixTime,
                                                    ServiceContext context,
                                                    ServiceCallback<List<NoteJobInfo>> callback)
      throws IOException {
    if (!conf.isJobManagerEnabled()) {
      return new ArrayList<>();
    }

    List<NoteJobInfo> notesJobInfo = notebook.getNoteStream()
            .filter(note -> authorizationService.isOwner(context.getUserAndRoles(), note.getId()))
            .map(NoteJobInfo::new)
            .filter(noteJobInfo -> noteJobInfo.unixTimeLastRun > lastUpdateServerUnixTime)
            .collect(Collectors.toList());

    callback.onSuccess(notesJobInfo, context);
    return notesJobInfo;
  }

  public void removeNoteJobInfo(String noteId,
                                ServiceContext context,
                                ServiceCallback<List<NoteJobInfo>> callback) throws IOException {
    if (!conf.isJobManagerEnabled()) {
      return;
    }
    List<NoteJobInfo> notesJobInfo = new ArrayList<>();
    notesJobInfo.add(new NoteJobInfo(noteId, true));
    callback.onSuccess(notesJobInfo, context);
  }

  private static long getUnixTimeLastRunParagraph(Paragraph paragraph) {
    if (paragraph.isTerminated() && paragraph.getDateFinished() != null) {
      return paragraph.getDateFinished().getTime();
    } else if (paragraph.isRunning()) {
      return new Date().getTime();
    } else {
      return paragraph.getDateCreated().getTime();
    }
  }

  /**
   * Job info about one paragraph run
   */
  public static class ParagraphJobInfo {
    private String id;
    private String name;
    private Job.Status status;

    public ParagraphJobInfo(Paragraph p) {
      this.id = p.getId();
      if (StringUtils.isBlank(p.getTitle())) {
        this.name = p.getId();
      } else {
        this.name = p.getTitle();
      }
      this.status = p.getStatus();
    }
  }

  /**
   * Job info about note run, including all the job infos of paragraph run.
   */
  public static class NoteJobInfo {
    private String noteId;
    private String noteName;
    private String noteType;
    /**
     * default interpreterGroup.
     */
    private String interpreter;
    private boolean isRunningJob;
    private boolean isRemoved = false;
    private long unixTimeLastRun;
    private List<ParagraphJobInfo> paragraphs;

    public NoteJobInfo(Note note) {
      boolean isNoteRunning = false;
      long lastRunningUnixTime = 0;
      this.noteId = note.getId();
      this.noteName = note.getName();
      // set note type ( cron or normal )
      if (isCron(note)) {
        this.noteType = "cron";
      } else {
        this.noteType = "normal";
      }
      this.interpreter = note.getDefaultInterpreterGroup();

      // set paragraphs
      this.paragraphs = new ArrayList<>();
      for (Paragraph paragraph : note.getParagraphs()) {
        // check paragraph's status.
        if (paragraph.getStatus().isRunning()) {
          isNoteRunning = true;
        }
        // get data for the job manager.
        ParagraphJobInfo paragraphItem = new ParagraphJobInfo(paragraph);
        lastRunningUnixTime = getUnixTimeLastRunParagraph(paragraph);
        paragraphs.add(paragraphItem);
      }

      this.isRunningJob = isNoteRunning;
      this.unixTimeLastRun = lastRunningUnixTime;
    }

    private boolean isCron(Note note) {
      return note.getConfig().containsKey("cron") &&
          !StringUtils.isBlank(note.getConfig().get("cron").toString());
    }

    public NoteJobInfo(String noteId, boolean isRemoved) {
      this.noteId = noteId;
      this.isRemoved = isRemoved;
    }
  }
}
