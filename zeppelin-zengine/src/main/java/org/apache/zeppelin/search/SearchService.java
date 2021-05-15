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
package org.apache.zeppelin.search;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventAsyncListener;
import org.apache.zeppelin.notebook.Paragraph;

import javax.annotation.PreDestroy;

/**
 * Search (both, indexing and query) the notes.
 * 
 * Intended to have multiple implementation, i.e:
 *  - local Lucene (in-memory, on-disk)
 *  - remote Elasticsearch
 */
public abstract class SearchService extends NoteEventAsyncListener {

  public SearchService(String name) {
    super(name);
  }

  /**
   * Full-text search in all the notes
   *
   * @param queryStr a query
   * @return A list of matching paragraphs (id, text, snippet w/ highlight)
   */
  public abstract List<Map<String, String>> query(String queryStr);

  /**
   * Updates note index for the given note, only update index of note meta info,
   * such as id,name. Paragraph index will be done in method updateParagraphIndex.
   *
   * @param note a Note to update index for
   * @throws IOException
   */
  public abstract void updateNoteIndex(Note note) throws IOException;

  /**
   * Updates paragraph index for the given paragraph.
   *
   * @param paragraph a Paragraph to update index for
   * @throws IOException
   */

  public abstract void updateParagraphIndex(Paragraph paragraph) throws IOException;

  /**
   * Indexes the given note.
   *
   * @throws IOException If there is a low-level I/O error
   */
  public abstract void addNoteIndex(Note note) throws IOException;

  /**
   * Indexes the given paragraph.
   *
   * @throws IOException If there is a low-level I/O error
   */
  public abstract void addParagraphIndex(Paragraph pargaraph) throws IOException;


  /**
   * Deletes all docs on given Note from index
   */
  public abstract void deleteNoteIndex(Note note) throws IOException;

  /**
   * Deletes doc for a given
   *
   * @param noteId
   * @param p
   * @throws IOException
   */
  public abstract void deleteParagraphIndex(String noteId, Paragraph p) throws IOException;

  /**
   * Frees the recourses used by index
   */
  @PreDestroy
  public void close() {
    super.close();
  }

  @Override
  public void handleNoteCreateEvent(NoteCreateEvent noteCreateEvent) throws Exception {
    addNoteIndex(noteCreateEvent.getNote());
  }

  @Override
  public void handleNoteRemoveEvent(NoteRemoveEvent noteRemoveEvent) throws Exception {
    deleteNoteIndex(noteRemoveEvent.getNote());
  }

  @Override
  public void handleNoteUpdateEvent(NoteUpdateEvent noteUpdateEvent) throws Exception {
    updateNoteIndex(noteUpdateEvent.getNote());
  }

  @Override
  public void handleParagraphCreateEvent(ParagraphCreateEvent paragraphCreateEvent) throws Exception {
    addParagraphIndex(paragraphCreateEvent.getParagraph());
  }

  @Override
  public void handleParagraphRemoveEvent(ParagraphRemoveEvent paragraphRemoveEvent) throws Exception {
    Paragraph p = paragraphRemoveEvent.getParagraph();
    deleteParagraphIndex(p.getNote().getId(), p);
  }

  @Override
  public void handleParagraphUpdateEvent(ParagraphUpdateEvent paragraphUpdateEvent) throws Exception {
    updateParagraphIndex(paragraphUpdateEvent.getParagraph());
  }

  public abstract void startRebuildIndex(Stream<Note> notes);
}
