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
package org.apache.zeppelin.helium;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;

import java.util.List;

/**
 * Angular Registry for helium app
 */
public class HeliumAppAngularObjectRegistry {
  private final String noteId;
  private final String appId;
  private final AngularObjectRegistry angularObjectRegistry;

  public HeliumAppAngularObjectRegistry(AngularObjectRegistry angularObjectRegistry,
                                        String noteId,
                                        String appId) {
    this.angularObjectRegistry = angularObjectRegistry;
    this.noteId = noteId;
    this.appId = appId;
  }

  public AngularObject add(String name, Object o) {
    return angularObjectRegistry.add(name, o, noteId, appId);
  }

  public AngularObject remove(String name) {
    return angularObjectRegistry.remove(name, noteId, appId);
  }

  public AngularObject get(String name) {
    return angularObjectRegistry.get(name, noteId, appId);
  }

  public List<AngularObject> getAll() {
    return angularObjectRegistry.getAll(noteId, appId);
  }
}
