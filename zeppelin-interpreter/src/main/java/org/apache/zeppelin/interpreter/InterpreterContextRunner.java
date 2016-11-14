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

/**
 */
public abstract class InterpreterContextRunner implements Runnable {
  String noteId;
  private String paragraphId;

  public InterpreterContextRunner(String noteId, String paragraphId) {
    this.noteId = noteId;
    this.paragraphId = paragraphId;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof InterpreterContextRunner) {
      InterpreterContextRunner io = ((InterpreterContextRunner) o);
      if (io.getParagraphId().equals(paragraphId) &&
          io.getNoteId().equals(noteId)) {
        return true;
      } else {
        return false;
      }

    } else {
      return false;
    }
  }

  @Override
  public abstract void run();

  public String getNoteId() {
    return noteId;
  }

  public String getParagraphId() {
    return paragraphId;
  }

}
