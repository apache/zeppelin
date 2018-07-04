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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class InterpreterInstallationRequest {
  private static final Gson gson = new GsonBuilder().create();
  private String name;
  private String artifact;

  public InterpreterInstallationRequest(String name, String artifact) {
    this.name = name;
    this.artifact = artifact;
  }

  public static InterpreterInstallationRequest fromJson(String message) {
    return gson.fromJson(message, InterpreterInstallationRequest.class);
  }

  public String getName() {
    return name;
  }

  public String getArtifact() {
    return artifact;
  }
}
