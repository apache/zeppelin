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

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Helium package registry
 */
public abstract class HeliumRegistry {
  private final String name;
  private final String uri;

  public HeliumRegistry(String name, String uri) {
    this.name = name;
    this.uri = uri;
  }
  public String name() {
    return name;
  }
  public String uri() {
    return uri;
  }
  public abstract List<HeliumPackage> getAll() throws IOException;
}
