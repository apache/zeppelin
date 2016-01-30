/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.springxd;

import static java.lang.Math.min;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Completion utility that helps to adapt the SpringXD completion API with the ACE editor
 * requirements.
 */
public abstract class AbstractSpringXdResourceCompletion {

  private Logger logger = LoggerFactory.getLogger(AbstractSpringXdResourceCompletion.class);

  public static final String EMPTY_PREFFIX = "";

  public static final String LINE_SEPARATOR = System.getProperty("line.separator");

  public static final List<String> EMPTY_ZEPPELIN_COMPLETION = null;

  public static final int SINGLE_LEVEL_OF_DETAILS = 1;

  /**
   * To be implemented by the underlying SpringXD mechanism (Stream or Job) and use the SpringXD
   * completion API.
   * 
   * @param completionPreffix - input completion string
   * @return Returns completions in SpringXD format
   */
  public abstract List<String> doSpringXdCompletion(String completionPreffix);

  /**
   * Delegates the completion to the underlying doSpringXdCompletion method and transforms the
   * completion results from SpringXD into ACE completions
   * 
   * @param buf - input buffer from zeppelin editor
   * @param cursor - cursor position in the zeppelin editor
   * @return Returns completions adapted for the ACE editor.
   */
  public List<String> completion(String buf, int cursor) {
    List<String> zeppelinCompletions = null;

    if (buf != null) {
      try {

        String completionPreffix = getCompletionPreffix(buf, cursor);

        logger.debug("Buffer [" + buf + "], Len=" + buf.length() + ", Cursor=" + cursor
            + ", Preffix [" + completionPreffix + "]");

        List<String> xdCompletions = this.doSpringXdCompletion(completionPreffix);

        zeppelinCompletions = convertXdToZeppelinCompletions(xdCompletions, completionPreffix);

      } catch (Exception e) {
        logger.warn("Completion error", e);
      }
    }
    return zeppelinCompletions;

  }

  /**
   * In multi-line buffer returns the a line part that starts with the line where the cursor is
   * position until the cursor position (or the end of the line when the cursor spans beyond the
   * buffer length)
   * 
   * @param buffer - Input multi line buffer
   * @param cursor - Cursor position in the buffer.
   * @return Returns a line that start at the begining of the line cursor is positioned until the
   *         cursor position (or the end of the line).
   */
  String getCompletionPreffix(String buffer, int cursor) {

    if (isBlank(buffer)) {
      return EMPTY_PREFFIX;
    }

    // For completion we need only the first half of the buffer up to the cursor. The cursor can go
    // beyond the length of the buffer. If this is the case use the end of the buffer.
    int endIndex = min(buffer.length(), cursor - 1);
    endIndex = (endIndex > 0) ? endIndex : 0;
    String bufToCursorHalf = buffer.substring(0, endIndex);

    // Separate the line where the cursor is positioned. It could be at the begining in the middle
    // or after the buffer. From the cursor position (or the end of the buffer) go backwards to find
    // the first occurrence of end-of-line character (or the start of the buffer)
    int lastIndexOfLineSeparator = StringUtils.lastIndexOfAny(bufToCursorHalf, LINE_SEPARATOR) + 1;

    String preffix = buffer.substring(lastIndexOfLineSeparator, endIndex);

    return preffix;
  }

  /**
   * SpringXD's completion response contains the entire line (including the prefix part already
   * existing in the buffer). To be able to insert those completion within the ACE editor the
   * prefixes have to be removed form the xd completion results.
   * 
   * @param xdCompletions Spring XD completion result list.
   * @param preffix String completion prefix use to search completions for.
   * @return Returns a modified list of the same size. Each element in the list has the prefix part
   *         removed.
   */
  List<String> convertXdToZeppelinCompletions(List<String> xdCompletions, String preffix) {

    // Noting to filter
    if (isBlank(preffix)) {
      return xdCompletions;
    }

    List<String> zeppelinCompletions = EMPTY_ZEPPELIN_COMPLETION;

    if (!isEmpty(xdCompletions)) {

      zeppelinCompletions = new ArrayList<String>();

      String preffixToReplace = preffix.substring(0, getLastWhitespaceIndex(preffix) + 1);

      for (String c : xdCompletions) {

        String zeppelinCompletion = c.replace(preffixToReplace.trim(), "").trim();

        zeppelinCompletions.add(zeppelinCompletion);
      }
    }

    return zeppelinCompletions;
  }

  private int getLastWhitespaceIndex(String s) {
    if (!isBlank(s)) {
      for (int i = s.length() - 1; i >= 0; i--) {
        if (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == '|' || s.charAt(i) == '=') {
          return i;
        }
      }
    }
    return 0;
  }
}
