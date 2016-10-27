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
package org.apache.zeppelin.notebook.socket;

import com.google.gson.Gson;

/**
 * Zeppelin websocket massage template class for watcher socket.
 */
public class WatcherMessage {

  public String message;
  public String noteId;
  public String subject;
  
  private static final Gson gson = new Gson();
  
  public static Builder builder(String noteId) {
    return new Builder(noteId);
  }
  
  private WatcherMessage(Builder builder) {
    this.noteId = builder.noteId;
    this.message = builder.message;
    this.subject = builder.subject;
  }
  
  public String serialize() {
    return gson.toJson(this);
  }
  
  /**
   * Simple builder.
   */
  public static class Builder {
    private final String noteId;
    private String subject;
    private String message;
    
    public Builder(String noteId) {
      this.noteId = noteId;
    }
    
    public Builder subject(String subject) {
      this.subject = subject;
      return this;
    }
    
    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public WatcherMessage build() {
      return new WatcherMessage(this);
    }
  }
  
}
