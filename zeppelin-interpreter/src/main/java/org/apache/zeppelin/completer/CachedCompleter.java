/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.completer;

import jline.console.completer.Completer;

/**
 * Completer with time to live
 */
public class CachedCompleter {
  private Completer completer;
  private int ttlInSeconds;
  private long createdAt;

  public CachedCompleter(Completer completer, int ttlInSeconds) {
    this.completer = completer;
    this.ttlInSeconds = ttlInSeconds;
    this.createdAt = System.currentTimeMillis();
  }

  public boolean isExpired() {
    if (ttlInSeconds == -1 || (ttlInSeconds > 0 &&
        (System.currentTimeMillis() - createdAt) / 1000 > ttlInSeconds)) {
      return true;
    }
    return false;
  }

  public Completer getCompleter() {
    return completer;
  }
}
