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

package org.apache.zeppelin.dep;
import org.sonatype.aether.repository.Authentication;
/**
 *
 *
 */
public class Repository {
  private boolean snapshot = false;
  private String id;
  private String url;
  private String username = null;
  private String password = null;

  public Repository(String id){
    this.id = id;
  }

  public Repository url(String url) {
    this.url = url;
    return this;
  }

  public Repository snapshot() {
    snapshot = true;
    return this;
  }

  public boolean isSnapshot() {
    return snapshot;
  }

  public String getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }
  
  public Repository username(String username) {
    this.username = username;
    return this;
  }
  
  public Repository password(String password) {
    this.password = password;
    return this;
  }
  
  public Repository credentials(String username, String password) {
    this.username = username;
    this.password = password;
    return this;
  }
  
  public Authentication getAuthentication() {
    Authentication auth = null;
    if (this.username != null && this.password != null) {
      auth = new Authentication(this.username, this.password);
    }
    return auth;
  }
}
