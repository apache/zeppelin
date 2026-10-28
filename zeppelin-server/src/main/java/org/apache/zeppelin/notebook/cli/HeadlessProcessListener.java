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

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Headless implementation of {@link RemoteInterpreterProcessListener}. Only
 * {@link #onOutputAppend} and {@link #onOutputUpdated} are forwarded to stdout so a blocking
 * CLI run still shows progress; {@link #onOutputClear} and {@link #checkpointOutput} are
 * debug-logged only. {@link #getParagraphList} and {@link #runParagraphs} delegate to the
 * local {@link Notebook} because some interpreters synchronously depend on them to chain
 * paragraph execution (see {@code NotebookServer#runParagraphs}/{@code #getParagraphList} for
 * the reference implementation this mirrors, minus the UI-only READER permission check and the
 * paragraphIds/paragraphIndices mutual-exclusion guard, both dropped for this single-user
 * headless run).
 */
public class HeadlessProcessListener implements RemoteInterpreterProcessListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HeadlessProcessListener.class);

  private final ExecutorService runParagraphsExecutor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "HeadlessProcessListener-runParagraphs");
    t.setDaemon(true);
    return t;
  });

  private volatile Notebook notebook;

  public void setNotebook(Notebook notebook) {
    this.notebook = notebook;
  }

  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String output) {
    System.out.print(output);
    System.out.flush();
  }

  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index,
      InterpreterResult.Type type, String output) {
    System.out.println(output);
  }

  @Override
  public void onOutputClear(String noteId, String paragraphId) {
    LOGGER.debug("Output cleared for note {} paragraph {}", noteId, paragraphId);
  }

  @Override
  public void runParagraphs(String noteId, List<Integer> paragraphIndices,
      List<String> paragraphIds, String curParagraphId) throws IOException {
    Notebook nb = requireNotebook();
    nb.processNote(noteId, note -> {
      if (note == null) {
        throw new IOException("Not existed noteId: " + noteId);
      }
      List<String> toBeRunParagraphIds = new ArrayList<>();
      if (paragraphIds != null && !paragraphIds.isEmpty()) {
        for (String paragraphId : paragraphIds) {
          if (note.getParagraph(paragraphId) == null) {
            throw new IOException("Not existed paragraphId: " + paragraphId);
          }
          if (!paragraphId.equals(curParagraphId)) {
            toBeRunParagraphIds.add(paragraphId);
          }
        }
      } else if (paragraphIndices != null && !paragraphIndices.isEmpty()) {
        for (int paragraphIndex : paragraphIndices) {
          Paragraph p = note.getParagraph(paragraphIndex);
          if (p == null) {
            throw new IOException("Not existed paragraphIndex: " + paragraphIndex);
          }
          if (!p.getId().equals(curParagraphId)) {
            toBeRunParagraphIds.add(p.getId());
          }
        }
      } else {
        for (Paragraph p : note.getParagraphs()) {
          if (!p.getId().equals(curParagraphId)) {
            toBeRunParagraphIds.add(p.getId());
          }
        }
      }
      runParagraphsExecutor.submit(() -> {
        for (String paragraphId : toBeRunParagraphIds) {
          try {
            note.run(paragraphId, true);
          } catch (Exception e) {
            LOGGER.warn("Fail to run paragraph {} of note {}", paragraphId, noteId, e);
          }
        }
      });
      return null;
    });
  }

  @Override
  public void onParaInfosReceived(String noteId, String paragraphId,
      String interpreterSettingId, Map<String, String> metaInfos) {
    LOGGER.debug("Paragraph info received for note {} paragraph {}: {}", noteId, paragraphId,
        metaInfos);
  }

  @Override
  public List<ParagraphInfo> getParagraphList(String user, String noteId)
      throws TException, IOException {
    Notebook nb = requireNotebook();
    return nb.processNote(noteId, note -> {
      if (note == null) {
        throw new IOException("Not found this note: " + noteId);
      }
      List<ParagraphInfo> paragraphInfos = new ArrayList<>();
      for (Paragraph paragraph : note.getParagraphs()) {
        ParagraphInfo paraInfo = new ParagraphInfo();
        paraInfo.setNoteId(noteId);
        paraInfo.setParagraphId(paragraph.getId());
        paraInfo.setParagraphTitle(paragraph.getTitle());
        paraInfo.setParagraphText(paragraph.getText());
        paragraphInfos.add(paraInfo);
      }
      return paragraphInfos;
    });
  }

  @Override
  public void checkpointOutput(String noteId, String paragraphId) {
    LOGGER.debug("Checkpoint output for note {} paragraph {}", noteId, paragraphId);
  }

  private Notebook requireNotebook() {
    Notebook nb = notebook;
    if (nb == null) {
      throw new IllegalStateException(
          "Notebook is not set yet. HeadlessProcessListener.setNotebook must be called "
              + "before any interpreter callback can be served.");
    }
    return nb;
  }

  void closeExecutor() {
    runParagraphsExecutor.shutdown();
    try {
      if (!runParagraphsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        LOGGER.warn("In-flight runParagraphs task(s) did not finish within 5s, forcing shutdown");
        runParagraphsExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      runParagraphsExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
