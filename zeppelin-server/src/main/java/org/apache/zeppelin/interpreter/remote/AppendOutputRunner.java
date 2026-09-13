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

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Sends paragraph output periodically. Adjacent append events are batched, while update events
 * share the same queue so that they cannot overtake earlier appends.
 */
public class AppendOutputRunner implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppendOutputRunner.class);
  public static final Long BUFFER_TIME_MS = Long.valueOf(100);
  private static final Long SAFE_PROCESSING_TIME = Long.valueOf(10);
  private static final Long SAFE_PROCESSING_STRING_SIZE = Long.valueOf(100000);

  private final BlockingQueue<AppendOutputBuffer> queue = new LinkedBlockingQueue<>();
  private final RemoteInterpreterProcessListener listener;

  public AppendOutputRunner(RemoteInterpreterProcessListener listener) {
    this.listener = listener;
  }

  // Serialize scheduled and RPC drains to preserve callback order.
  // Empty drains must return immediately to RPC callers.
  @Override
  public synchronized void run() {

    Map<AppendKey, StringBuilder> stringBufferMap = new LinkedHashMap<>();
    List<AppendOutputBuffer> list = new LinkedList<>();

    queue.drainTo(list);
    if (list.isEmpty()) {
      return;
    }
    Long processingStartTime = System.currentTimeMillis();

    Long sizeProcessed = Long.valueOf(0);
    for (AppendOutputBuffer buffer : list) {
      if (buffer instanceof UpdateOutputBuffer) {
        sizeProcessed += flushAppendBuffers(stringBufferMap);
        UpdateOutputBuffer update = (UpdateOutputBuffer) buffer;
        try {
          listener.onParagraphOutputUpdated(update.getNoteId(), update.getParagraphId(),
              update.getIndex(), update.getUser(), update.getType(), update.getData());
        } catch (RuntimeException e) {
          // A stale callback must not abort another paragraph's synchronous drain.
          LOGGER.warn("Failed to update output for note {} paragraph {}",
              update.getNoteId(), update.getParagraphId(), e);
        }
        continue;
      }

      // The execution owner is part of the key: two users running the same paragraph must not
      // have their output folded into one chunk, because the merged chunk would have no owner.
      AppendKey key = new AppendKey(buffer.getNoteId(), buffer.getParagraphId(),
          buffer.getIndex(), buffer.getUser());

      stringBufferMap.computeIfAbsent(key, unused -> new StringBuilder())
          .append(buffer.getData());
    }
    sizeProcessed += flushAppendBuffers(stringBufferMap);
    Long processingTime = System.currentTimeMillis() - processingStartTime;

    if (processingTime > SAFE_PROCESSING_TIME) {
      LOGGER.warn("Processing time for buffered append-output is high: {} milliseconds.", processingTime);
    } else {
      LOGGER.debug("Processing time for append-output took {} milliseconds", processingTime);
    }

    if (sizeProcessed > SAFE_PROCESSING_STRING_SIZE) {
      LOGGER.warn("Processing size for buffered append-output is high: {} characters.", sizeProcessed);
    } else {
      LOGGER.debug("Processing size for append-output is {} characters", sizeProcessed);
    }
  }

  private long flushAppendBuffers(Map<AppendKey, StringBuilder> stringBufferMap) {
    long sizeProcessed = 0;
    for (Entry<AppendKey, StringBuilder> stringBufferMapEntry : stringBufferMap.entrySet()) {
      AppendKey key = stringBufferMapEntry.getKey();
      StringBuilder buffer = stringBufferMapEntry.getValue();
      sizeProcessed += buffer.length();
      try {
        listener.onParagraphOutputAppend(key.noteId, key.paragraphId, key.index, key.user,
            buffer.toString());
      } catch (RuntimeException e) {
        // One stale append must not abort another paragraph's synchronous drain.
        LOGGER.warn("Failed to append output for {}", key, e);
      }
    }
    stringBufferMap.clear();
    return sizeProcessed;
  }

  /**
   * Identifies one stream of appended output. A user name can contain any character, so the
   * parts are kept separate instead of being joined into a delimited string.
   */
  private static final class AppendKey {
    private final String noteId;
    private final String paragraphId;
    private final int index;
    private final String user;

    private AppendKey(String noteId, String paragraphId, int index, String user) {
      this.noteId = noteId;
      this.paragraphId = paragraphId;
      this.index = index;
      this.user = user;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AppendKey)) {
        return false;
      }
      AppendKey other = (AppendKey) o;
      return index == other.index
          && Objects.equals(noteId, other.noteId)
          && Objects.equals(paragraphId, other.paragraphId)
          && Objects.equals(user, other.user);
    }

    @Override
    public int hashCode() {
      return Objects.hash(noteId, paragraphId, index, user);
    }

    @Override
    public String toString() {
      return "note " + noteId + " paragraph " + paragraphId + " index " + index + " user " + user;
    }
  }

  public void appendBuffer(String noteId, String paragraphId, int index, String user,
                           String outputToAppend) {
    queue.offer(new AppendOutputBuffer(noteId, paragraphId, index, user, outputToAppend));
  }

  /** Enqueues a replacement; callers needing completion must also invoke run(). */
  public void updateBuffer(String noteId, String paragraphId, int index, String user,
                           InterpreterResult.Type type, String output) {
    queue.offer(new UpdateOutputBuffer(noteId, paragraphId, index, user, type, output));
  }
}
