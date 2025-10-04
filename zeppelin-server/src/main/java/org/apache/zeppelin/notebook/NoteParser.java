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

package org.apache.zeppelin.notebook;

import org.apache.zeppelin.notebook.exception.CorruptedNoteException;

public interface NoteParser {

  /**
   * This method deserializes the specified Note Json into an Note object
   *
   * @param noteId - NoteId of the note
   * @param json - The Json String of the specified Note
   * @return A new Note object
   * @throws CorruptedNoteException is thrown if the note cannot be deserialized
   */
  Note fromJson(String noteId, String json) throws CorruptedNoteException;

  /**
   * This method deserializes the specified Paragraph Json into an Paragraph object
   *
   * @param json - The Json String of the specified Paragraph
   * @return A new Paragraph object
   */
  Paragraph fromJson(String json);

  /**
   * This method serializes the Note object into its equivalent Json representation
   *
   * @param note
   * @return JsonString of the Note
   */
  String toJson(Note note);

  /**
   * This method serializes the Paragraph object into its equivalent Json representation
   *
   * @param paragraph
   * @return JsonString of the Paragraph
   */
  String toJson(Paragraph paragraph);
}
