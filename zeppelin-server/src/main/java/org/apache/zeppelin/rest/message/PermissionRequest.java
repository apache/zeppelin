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

import java.util.Set;

/**
 * PermissionRequest rest api request message.
 */
public class PermissionRequest {
  private final Set<String> readers;
  private final Set<String> runners;
  private final Set<String> owners;
  private final Set<String> writers;

  public PermissionRequest(Set<String> readers, Set<String> runners, Set<String> owners, Set<String> writers) {
    this.readers = readers;
    this.runners = runners;
    this.owners = owners;
    this.writers = writers;
  }

  public Set<String> getReaders() {
    return readers;
  }

  public Set<String> getRunners() {
    return runners;
  }

  public Set<String> getOwners() {
    return owners;
  }

  public Set<String> getWriters() {
    return writers;
  }

}
