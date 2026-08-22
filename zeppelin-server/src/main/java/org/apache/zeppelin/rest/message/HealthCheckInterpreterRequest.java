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

package org.apache.zeppelin.rest.message;

/**
 * HealthCheckInterpreter rest api request message.
 *
 * <p>The note is what a caller who is not an administrator is authorized against, the same way a
 * restart from a note page is. A request without one is only served to whoever the deployment lets
 * reach the interpreter endpoints at all.
 */
public class HealthCheckInterpreterRequest {

  private final String noteId;

  public HealthCheckInterpreterRequest(String noteId) {
    this.noteId = noteId;
  }

  public String getNoteId() {
    return noteId;
  }
}
