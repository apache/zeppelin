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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This thread sends paragraph's append-data
 * periodically, rather than continously, with
 * a period of BUFFER_TIME_MS. It handles append-data
 * for all paragraphs across all notebooks.
 */
public class AppendOutputRunner implements Runnable {

  private static final Logger logger =
      LoggerFactory.getLogger(AppendOutputRunner.class);
  public static final Long BUFFER_TIME_MS = new Long(100);
  private static final Long SAFE_PROCESSING_TIME = new Long(10);
  private static final Long SAFE_PROCESSING_STRING_SIZE = new Long(100000);

  private final BlockingQueue<AppendOutputBuffer> queue = new LinkedBlockingQueue<>();
  private final RemoteInterpreterProcessListener listener;

  public AppendOutputRunner(RemoteInterpreterProcessListener listener) {
    this.listener = listener;
  }

  @Override
  public void run() {

    Map<String, StringBuilder> stringBufferMap = new HashMap<>();
    List<AppendOutputBuffer> list = new LinkedList<>();

    /* "drainTo" method does not wait for any element
     * to be present in the queue, and thus this loop would
     * continuosly run (with period of BUFFER_TIME_MS). "take()" method
     * waits for the queue to become non-empty and then removes
     * one element from it. Rest elements from queue (if present) are
     * removed using "drainTo" method. Thus we save on some un-necessary
     * cpu-cycles.
     */
    try {
      list.add(queue.take());
    } catch (InterruptedException e) {
      logger.error("Wait for OutputBuffer queue interrupted: " + e.getMessage());
    }
    Long processingStartTime = System.currentTimeMillis();
    queue.drainTo(list);

    for (AppendOutputBuffer buffer: list) {
      String noteId = buffer.getNoteId();
      String paragraphId = buffer.getParagraphId();
      int index = buffer.getIndex();
      String stringBufferKey = noteId + ":" + paragraphId + ":" + index;

      StringBuilder builder = stringBufferMap.containsKey(stringBufferKey) ?
          stringBufferMap.get(stringBufferKey) : new StringBuilder();

      builder.append(buffer.getData());
      stringBufferMap.put(stringBufferKey, builder);
    }
    Long processingTime = System.currentTimeMillis() - processingStartTime;

    if (processingTime > SAFE_PROCESSING_TIME) {
      logger.warn("Processing time for buffered append-output is high: " +
          processingTime + " milliseconds.");
    } else {
      logger.debug("Processing time for append-output took "
          + processingTime + " milliseconds");
    }

    Long sizeProcessed = new Long(0);
    for (String stringBufferKey : stringBufferMap.keySet()) {
      StringBuilder buffer = stringBufferMap.get(stringBufferKey);
      sizeProcessed += buffer.length();
      String[] keys = stringBufferKey.split(":");
      listener.onOutputAppend(keys[0], keys[1], Integer.parseInt(keys[2]), buffer.toString());
    }

    if (sizeProcessed > SAFE_PROCESSING_STRING_SIZE) {
      logger.warn("Processing size for buffered append-output is high: " +
          sizeProcessed + " characters.");
    } else {
      logger.debug("Processing size for append-output is " +
          sizeProcessed + " characters");
    }
  }

  public void appendBuffer(String noteId, String paragraphId, int index, String outputToAppend) {
    queue.offer(new AppendOutputBuffer(noteId, paragraphId, index, outputToAppend));
  }

}
