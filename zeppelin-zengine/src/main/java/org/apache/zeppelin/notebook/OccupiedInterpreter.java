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

import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Current occupied interpreter information for each note
 */
public class OccupiedInterpreter {
  private static final Pattern INTERPRETER_NAME_PATTERN = Pattern.compile("^%.*");
  private static final String DEFAULT_INTERPRETER_NAME = "%..";
  private static final HashMap<String, String> occupiedInterpreterMap = new HashMap<>();

  private OccupiedInterpreter() {
  }

  /**
   * Get current occupied interpreter name
   *
   * @param noteId Note Id
   * @return Current occupied interpreter name, or "" if no mapping occupied interpreter name.
   */
  public static String getOccupiedInterpreter(String noteId) {
    String occupiedInterpreter;
    synchronized (occupiedInterpreterMap) {
      occupiedInterpreter = occupiedInterpreterMap.get(noteId);
    }
    return StringUtils.defaultString(occupiedInterpreter, DEFAULT_INTERPRETER_NAME);
  }

  static String getDefaultInterpreterName() {
    return DEFAULT_INTERPRETER_NAME;
  }

  /**
   * Remove Occupied interpreter name
   *
   * @param noteId Note Id
   */
  public static void removeOccupiedInterpreter(String noteId) {
    if (StringUtils.isBlank(noteId)) {
      return;
    }
    synchronized (occupiedInterpreterMap) {
      occupiedInterpreterMap.remove(noteId);
    }
  }

  /**
   * Set current occupied interpreter name
   *
   * @param noteId          Note Id
   * @param interpreterName Current occupied interpreter name
   */
  public static void setOccupiedInterpreter(String noteId, String interpreterName) {
    if (StringUtils.isBlank(interpreterName)) {
      return;
    }

    synchronized (occupiedInterpreterMap) {
      occupiedInterpreterMap.put(noteId, interpreterName);
    }
  }

  /**
   * Set current occupied interpreter name
   *
   * @param p Paragraph
   */
  public static void setOccupiedInterpreter(Paragraph p) {
    if (p == null || p.getNote() == null || StringUtils.isEmpty(p.getNote().getId())
            || StringUtils.isEmpty(p.getText())) {
      return;
    }

    String interpreterName = parseInterpreterName(p.getText());
    if (interpreterName == null) {
      return;
    }

    setOccupiedInterpreter(p.getNote().getId(), interpreterName);
  }

  /**
   * Parse interpreter name
   *
   * @param text Paragraph's text
   * @return interpreter name, or {@code null} if text is blank or no interpreter name.
   */
  static String parseInterpreterName(String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }

    Matcher matcher = INTERPRETER_NAME_PATTERN.matcher(text);
    if (matcher.find()) {
      return matcher.group();
    }
    return null;
  }

  /**
   * Set interpreter name If Paragraph has empty text
   *
   * @param p Paragraph
   */
  public static void setInterpreterNameIfEmptyText(Paragraph p) {
    String noteId = p.getNote().getId();
    String interpreterName = getOccupiedInterpreter(noteId);
    String text = p.getText();
    if (StringUtils.isEmpty(text)) {
      p.setText(interpreterName);
    }
  }
}
