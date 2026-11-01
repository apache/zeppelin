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

package org.apache.zeppelin.interpreter.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Synchronously delivers an append batch, merging events for each paragraph output.
 */
public class AppendOutputRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(AppendOutputRunner.class);
  public static final Long BUFFER_TIME_MS = Long.valueOf(100);
  private static final Long SAFE_PROCESSING_TIME = Long.valueOf(10);
  private static final Long SAFE_PROCESSING_STRING_SIZE = Long.valueOf(100000);

  private final RemoteInterpreterProcessListener listener;

  public AppendOutputRunner(RemoteInterpreterProcessListener listener) {
    this.listener = listener;
  }

  public void run(List<AppendOutputBuffer> batch) {
    run(batch, () -> true);
  }

  /** Stops between append groups when delivery is disallowed; an active callback may finish. */
  void run(List<AppendOutputBuffer> batch, BooleanSupplier mayDeliver) {
    if (batch.isEmpty()) {
      return;
    }
    long start = System.currentTimeMillis();
    long size = 0;
    Map<ParagraphOutputKey, StringBuilder> groups = new LinkedHashMap<>();
    ParagraphOutputKey currentKey = null;
    StringBuilder currentData = null;
    for (AppendOutputBuffer append : batch) {
      if (currentKey == null || !currentKey.matches(append)) {
        currentKey = new ParagraphOutputKey(append);
        currentData = groups.computeIfAbsent(currentKey, key -> new StringBuilder());
      }
      currentData.append(append.getData());
    }
    for (Map.Entry<ParagraphOutputKey, StringBuilder> group : groups.entrySet()) {
      if (!mayDeliver.getAsBoolean()) {
        return;
      }
      size += flush(group.getKey(), group.getValue());
    }
    long time = System.currentTimeMillis() - start;
    if (time > SAFE_PROCESSING_TIME) {
      LOGGER.warn("Processing time for buffered append-output is high: {} milliseconds.", time);
    } else {
      LOGGER.debug("Processing time for append-output took {} milliseconds", time);
    }
    if (size > SAFE_PROCESSING_STRING_SIZE) {
      LOGGER.warn("Processing size for buffered append-output is high: {} characters.", size);
    } else {
      LOGGER.debug("Processing size for append-output is {} characters", size);
    }
  }

  private long flush(ParagraphOutputKey key, StringBuilder data) {
    long size = data.length();
    try {
      listener.onParagraphOutputAppend(
          key.noteId, key.paragraphId, key.index, key.executionOwner, data.toString());
    } catch (RuntimeException e) {
      // A stale paragraph must not abort delivery of later output in this drain.
      LOGGER.warn("Failed to append output for note {} paragraph {}",
          key.noteId, key.paragraphId, e);
    }
    data.setLength(0);
    return size;
  }

  private static final class ParagraphOutputKey {
    private final String noteId;
    private final String paragraphId;
    private final int index;
    private final String executionOwner;

    private ParagraphOutputKey(AppendOutputBuffer append) {
      noteId = append.getNoteId();
      paragraphId = append.getParagraphId();
      index = append.getIndex();
      executionOwner = append.getExecutionOwner();
    }

    private boolean matches(AppendOutputBuffer append) {
      return index == append.getIndex()
          && Objects.equals(noteId, append.getNoteId())
          && Objects.equals(paragraphId, append.getParagraphId())
          && Objects.equals(executionOwner, append.getExecutionOwner());
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof ParagraphOutputKey)) {
        return false;
      }
      ParagraphOutputKey key = (ParagraphOutputKey) other;
      return index == key.index && Objects.equals(noteId, key.noteId)
          && Objects.equals(paragraphId, key.paragraphId)
          && Objects.equals(executionOwner, key.executionOwner);
    }

    @Override
    public int hashCode() {
      int hash = Objects.hashCode(noteId);
      hash = 31 * hash + Objects.hashCode(paragraphId);
      hash = 31 * hash + index;
      return 31 * hash + Objects.hashCode(executionOwner);
    }
  }
}
