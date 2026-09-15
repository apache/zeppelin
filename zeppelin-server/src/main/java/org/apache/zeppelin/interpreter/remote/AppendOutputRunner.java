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
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Synchronously delivers an append batch, merging adjacent events for the same paragraph output.
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
    AppendOutputBuffer previous = null;
    StringBuilder data = new StringBuilder();
    for (AppendOutputBuffer append : batch) {
      if (!mayDeliver.getAsBoolean()) {
        return;
      }
      if (previous != null && !sameOutput(previous, append)) {
        size += flush(previous, data);
        if (!mayDeliver.getAsBoolean()) {
          return;
        }
      }
      previous = append;
      data.append(append.getData());
    }
    if (!mayDeliver.getAsBoolean()) {
      return;
    }
    size += flush(previous, data);
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

  private boolean sameOutput(AppendOutputBuffer first, AppendOutputBuffer second) {
    return first.getIndex() == second.getIndex()
        && Objects.equals(first.getNoteId(), second.getNoteId())
        && Objects.equals(first.getParagraphId(), second.getParagraphId());
  }

  private long flush(AppendOutputBuffer append, StringBuilder data) {
    long size = data.length();
    try {
      listener.onOutputAppend(append.getNoteId(), append.getParagraphId(), append.getIndex(),
          data.toString());
    } catch (RuntimeException e) {
      // A stale paragraph must not abort delivery of later output in this drain.
      LOGGER.warn("Failed to append output for note {} paragraph {}",
          append.getNoteId(), append.getParagraphId(), e);
    }
    data.setLength(0);
    return size;
  }
}
