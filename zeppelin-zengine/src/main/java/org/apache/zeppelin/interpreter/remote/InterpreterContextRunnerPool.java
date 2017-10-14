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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class InterpreterContextRunnerPool {
  Logger logger = LoggerFactory.getLogger(InterpreterContextRunnerPool.class);
  private Map<String, List<InterpreterContextRunner>> interpreterContextRunners;

  public InterpreterContextRunnerPool() {
    interpreterContextRunners = new HashMap<>();

  }

  // add runner
  public void add(String noteId, InterpreterContextRunner runner) {
    synchronized (interpreterContextRunners) {
      if (!interpreterContextRunners.containsKey(noteId)) {
        interpreterContextRunners.put(noteId, new LinkedList<InterpreterContextRunner>());
      }

      interpreterContextRunners.get(noteId).add(runner);
    }
  }

  // replace all runners to noteId
  public void addAll(String noteId, List<InterpreterContextRunner> runners) {
    synchronized (interpreterContextRunners) {
      if (!interpreterContextRunners.containsKey(noteId)) {
        interpreterContextRunners.put(noteId, new LinkedList<InterpreterContextRunner>());
      }

      interpreterContextRunners.get(noteId).addAll(runners);
    }
  }

  public void clear(String noteId) {
    synchronized (interpreterContextRunners) {
      interpreterContextRunners.remove(noteId);
    }
  }


  public void run(String noteId, String paragraphId) {
    synchronized (interpreterContextRunners) {
      List<InterpreterContextRunner> list = interpreterContextRunners.get(noteId);
      if (list != null) {
        for (InterpreterContextRunner r : list) {
          if (noteId.equals(r.getNoteId()) && paragraphId.equals(r.getParagraphId())) {
            logger.info("run paragraph {} on note {} from InterpreterContext",
                r.getParagraphId(), r.getNoteId());
            r.run();
            return;
          }
        }
      }

      throw new RuntimeException("Can not run paragraph " + paragraphId + " on " + noteId);
    }
  }
}
