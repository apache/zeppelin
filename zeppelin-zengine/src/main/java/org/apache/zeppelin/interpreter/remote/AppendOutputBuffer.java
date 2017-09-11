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

/**
 * This element stores the buffered
 * append-data of paragraph's output.
 */
public class AppendOutputBuffer {

  private String noteId;
  private String paragraphId;
  private int index;
  private String data;

  public AppendOutputBuffer(String noteId, String paragraphId, int index, String data) {
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.index = index;
    this.data = data;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public int getIndex() {
    return index;
  }

  public String getData() {
    return data;
  }

}
