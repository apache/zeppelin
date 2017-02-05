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

import java.util.Map;

/**
 * LoadDynamicInterpreterRequest rest api request message
 */

public class DynamicInterpreterRequest {
  String artifact;
  String className;
  Map<String, Object> repository;

  public DynamicInterpreterRequest() {

  }

  public String getArtifact() { return artifact; }

  public String getClassName() { return className; }

  public Map<String, Object> getRepository() { return repository; }

  public String getUrl() throws ClassCastException {
    if (repository == null) {
      return null;
    }

    Object urlObj = repository.get("url");
    if (urlObj == null) {
      return null;
    }
    return (String) urlObj;
  }

  public Boolean isSnapshot() throws ClassCastException {
    if (repository == null) {
      return false;
    }
    Object snapshotFlagObj = repository.get("snapshot");
    if (snapshotFlagObj == null) {
      return false;
    }
    return (Boolean) snapshotFlagObj;
  }
}
