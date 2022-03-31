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

package org.apache.zeppelin.interpreter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Builtin Interpreter for running another notebook inline.
 * Multiple notes are supported. Notes run one by one. If one note fails to run, the remaining
 * notes are skipped. Parameters are supported as well.
 * Syntax:
 * %run
 * note1_id <param_1>=<value_1> <param_2>=<value_2> ...
 * note2_id <param_1>=<value_1> <param_2>=<value_2> ...
 */
public class RunNotebookInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(RunNotebookInterpreter.class);
  private static final String INTERPRETER_NAME = "_run_";
  private static final String INTERNAL_INTERPRETER_GROUP_ID = "_run_";

  private InterpreterGroup intpGroup;
  private Notebook notebook;
  private RemoteInterpreterProcessListener interpreterProcessListener;
  private AtomicReference<String> currentRunningNoteId;

  public RunNotebookInterpreter(InterpreterSettingManager interpreterSettingManager) {
    super(new Properties());
    this.notebook = interpreterSettingManager.getNotebook();
    this.interpreterProcessListener = interpreterSettingManager.getRemoteInterpreterProcessListener();

    InterpreterInfo interpreterInfo = new InterpreterInfo(
            RunNotebookInterpreter.class.getName(), INTERPRETER_NAME, false,
            Maps.newHashMap(), Maps.newHashMap());
    InterpreterSetting intpSetting = new InterpreterSetting.Builder()
            .setGroup(InterpreterSettingManager.INTERNAL_SETTING_NAME)
            .setId(InterpreterSettingManager.INTERNAL_SETTING_NAME)
            .setName(InterpreterSettingManager.INTERNAL_SETTING_NAME)
            .setIntepreterSettingManager(interpreterSettingManager)
            .setInterpreterInfos(Lists.newArrayList(interpreterInfo))
            .create();
    this.intpGroup = new ManagedInterpreterGroup(INTERNAL_INTERPRETER_GROUP_ID, intpSetting);
    this.currentRunningNoteId = new AtomicReference<>();
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  /**
   * Steps:
   *   - Step 1. Create a new paragraph in the last of current note.
   *   - Step 2. Parse the user code to get the
   *   - Step 3. Fill the new paragraph created in Step 1 with the new of running note and run it
   * @param st
   * @param context
   * @return
   * @throws InterpreterException
   */
  @Override
  protected InterpreterResult internalInterpret(String st, InterpreterContext context) throws InterpreterException {
    String[] lines = st.trim().split("\n");
    int outputIndex = 0;
    List<InterpreterResultMessage> allResultMessages = new ArrayList<>();
    String lastParagraphId = null;
    try {
      // create paragraph to the current note.
      Paragraph lastParagraph = notebook.processNote(context.getNoteId(), note ->
              note.addNewParagraph(AuthenticationInfo.ANONYMOUS));
      lastParagraphId = lastParagraph.getId();
      for (String line : lines) {
        String[] tokens = line.split("\\s");
        if (tokens.length == 0) {
          return new InterpreterResult(InterpreterResult.Code.ERROR,
                  String.format("Invalid usage: %s, please use %run <note_id> <param_1>=<value_1> <param_2>=<value_2> ...", line));
        }

        String noteId = tokens[0];
        if (context.getNoteId().equals(noteId)) {
          return new InterpreterResult(InterpreterResult.Code.ERROR,
                  "Run current note itself in %run is not allowed");
        }

        Map<String, String> params = parseParams(line.substring(noteId.length()).trim());

        InterpreterResult result = runNote(context, lastParagraph, noteId, params, outputIndex);
        outputIndex += result.message().size();
        allResultMessages.addAll(result.message());
        if (result.code != InterpreterResult.Code.SUCCESS) {
          return new InterpreterResult(result.code, allResultMessages);
        }
      }
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, allResultMessages);
    } catch (InvalidParameterFormatException e) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    } catch (IOException e) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
    } finally {
      if (lastParagraphId != null) {
        try {
          String finalLastParagraphId = lastParagraphId;
          notebook.processNote(context.getNoteId(), note -> {
            note.removeParagraph("anonymous", finalLastParagraphId);
            return null;
          });
        } catch (IOException e) {
          LOGGER.error(String.format("Fail to remove paragraph: %s", lastParagraphId), e);
        }
      }
    }
  }

  /**
   *
   * @param context
   * @param lastParagraph   last paragraph which is used to run
   * @param noteId          note to run
   * @param params          parameters for running note
   * @param outputStartIndex
   * @return
   */
  private InterpreterResult runNote(InterpreterContext context,
                                    Paragraph lastParagraph,
                                    String noteId,
                                    Map<String, String> params,
                                    int outputStartIndex) {
    List<InterpreterResultMessage> allResultMessages = new ArrayList<>();
    try {
      currentRunningNoteId.set(noteId);
      // copy execution note
      boolean isSuccess = notebook.processNote(noteId, note -> {
        if (note == null) {
          allResultMessages.add(new InterpreterResultMessage(InterpreterResult.Type.TEXT,
                  String.format("Note `%s` doesn't exist", noteId)));
          return false;
        }
        int outputIndex = outputStartIndex;
        for (Paragraph p : note.getParagraphs()) {
          if (StringUtils.isBlank(p.getScriptText())) {
            // skip empty paragraph
            continue;
          }
          lastParagraph.setTitle(p.getTitle());
          lastParagraph.setText(p.getText());
          lastParagraph.setConfig(p.getConfig());
          lastParagraph.settings.setParams(new HashMap<>(p.settings.getParams()));
          lastParagraph.settings.getParams().putAll(params);
          lastParagraph.settings.setForms(new HashMap<>(p.settings.getForms()));
          lastParagraph.run();
          InterpreterResult result = lastParagraph.getReturn();
          for (InterpreterResultMessage resultMessage : result.message()) {
            interpreterProcessListener.onOutputUpdated(context.getNoteId(), context.getParagraphId(), outputIndex ++,
                    resultMessage.getType(), resultMessage.getData());
            allResultMessages.add(resultMessage);
          }
          if (result.code != InterpreterResult.Code.SUCCESS) {
            return false;
          }
        }
        return true;
      });

      if (isSuccess) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, allResultMessages);
      } else {
        return new InterpreterResult(InterpreterResult.Code.ERROR, allResultMessages);
      }
    } catch (IOException e) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
    }
  }

  @VisibleForTesting
  public static Map<String, String> parseParams(String text) throws InvalidParameterFormatException {
    Map<String, String> params = new HashMap<>();
    int startPos = 0;
    String propKey = null;
    boolean insideQuotes = false, parseKey = true;
    StringBuilder sb = new StringBuilder();
    while(startPos < text.length()) {
      char ch = text.charAt(startPos);
      switch (ch) {
        case '\\': {
          if ((startPos + 1) == text.length()) {
            throw new InvalidParameterFormatException(
                    "Invalid parameter format. Unfinished escape sequence");
          }
          startPos++;
          ch = text.charAt(startPos);
          switch (ch) {
            case 'n':
              sb.append('\n');
              break;
            case 't':
              sb.append('\t');
              break;
            default:
              sb.append(ch);
          }
          break;
        }
        case '"': {
          insideQuotes = !insideQuotes;
          break;
        }
        case '=': {
          if (insideQuotes) {
            sb.append(ch);
          } else {
            if (!parseKey) {
              throw new InvalidParameterFormatException(
                      "Invalid parameter format");
            }
            propKey = sb.toString().trim();
            sb.delete(0, sb.length());
            parseKey = false;
          }
          break;
        }
        case ' ': {
          if (insideQuotes) {
            sb.append(ch);
          } else if (propKey == null || propKey.trim().isEmpty()) {
            throw new InvalidParameterFormatException(
                    "Invalid parameter format. key is empty");
          } else {
            if (parseKey) {
              propKey = sb.toString().trim();
              params.put(propKey, propKey);
            } else {
              params.put(propKey, sb.toString().trim());
            }
            propKey = null;
            parseKey = true;
            sb.delete(0, sb.length());
          }
          break;
        }
        default:
          sb.append(ch);
      }

      // last character
      if (startPos == text.length() - 1) {
        if (!insideQuotes) {
          if (parseKey) {
            throw new InvalidParameterFormatException("Invalid parameter format, value is empty");
          } else {
            params.put(propKey, sb.toString().trim());
          }
        } else {
          throw new InvalidParameterFormatException("Invalid parameter format, unfinished quote");
        }
      }

      startPos++;
    }

    return params;
  }

  @Override
  public void open() throws InterpreterException {

  }

  @Override
  public void close() throws InterpreterException {
    this.intpGroup = null;
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {

  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.NONE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public InterpreterGroup getInterpreterGroup() {
    return this.intpGroup;
  }

  public static class InvalidParameterFormatException extends Exception {
    public InvalidParameterFormatException(String msg) {
      super(msg);
    }
  }
}
