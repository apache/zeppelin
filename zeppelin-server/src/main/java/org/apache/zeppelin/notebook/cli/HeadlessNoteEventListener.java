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
package org.apache.zeppelin.notebook.cli;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless implementation of {@link NoteEventListener}. {@link #onParagraphStatusChange} is
 * printed to stdout so a blocking CLI run shows per-paragraph progress; the rest are debug-only
 * since there is no UI/index to notify in a headless run.
 */
public class HeadlessNoteEventListener implements NoteEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HeadlessNoteEventListener.class);

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    LOGGER.debug("Note removed: {}", note.getId());
  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    LOGGER.debug("Note created: {}", note.getId());
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) {
    LOGGER.debug("Note updated: {}", note.getId());
  }

  @Override
  public void onParagraphRemove(Paragraph p) {
    LOGGER.debug("Paragraph removed: {}", p.getId());
  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    LOGGER.debug("Paragraph created: {}", p.getId());
  }

  @Override
  public void onParagraphUpdate(Paragraph p) {
    LOGGER.debug("Paragraph updated: {}", p.getId());
  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {
    System.out.println("[" + p.getId() + "] " + status);
  }
}
