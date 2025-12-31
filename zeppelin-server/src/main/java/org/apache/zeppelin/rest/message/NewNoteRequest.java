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
package org.apache.zeppelin.rest.message;

import java.util.List;

/**
 *  NewNoteRequest rest api request message.
 */
public class NewNoteRequest {

  private final String notePath;
  private final String defaultInterpreterGroup;
  private final Boolean addingEmptyParagraph;
  private final List<NewParagraphRequest> paragraphs;
  private final String revisionId;

  public NewNoteRequest(String notePath, String defaultInterpreterGroup, Boolean addingEmptyParagraph, List<NewParagraphRequest> paragraphs, String revisionId) {
    this.notePath = notePath;
    this.defaultInterpreterGroup = defaultInterpreterGroup;
    this.addingEmptyParagraph = addingEmptyParagraph;
    this.paragraphs = paragraphs;
    this.revisionId = revisionId;
  }

  /**
   *
   * @return addingEmptyParagraph, in case of null false is returned
   */
  public boolean getAddingEmptyParagraph() {
    if (addingEmptyParagraph == null) {
      return false;
    }
    return addingEmptyParagraph.booleanValue();
  }

  public String getNotePath() {
    return notePath;
  }

  public String getDefaultInterpreterGroup() {
    return defaultInterpreterGroup;
  }

  public List<NewParagraphRequest> getParagraphs() {
    return paragraphs;
  }

  public String getRevisionId() {
    return revisionId;
  }
}
