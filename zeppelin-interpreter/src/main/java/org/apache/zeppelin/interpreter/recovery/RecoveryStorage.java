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

package org.apache.zeppelin.interpreter.recovery;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;

import java.io.IOException;
import java.util.Map;


/**
 * Interface for storing interpreter process recovery metadata.
 *
 */
public abstract class RecoveryStorage {

  protected ZeppelinConfiguration zConf;
  protected Map<String, InterpreterClient> restoredClients;

  public RecoveryStorage(ZeppelinConfiguration zConf) throws IOException {
    this.zConf = zConf;
  }

  /**
   * Update RecoveryStorage when new InterpreterClient is started
   * @param client
   * @throws IOException
   */
  public abstract void onInterpreterClientStart(InterpreterClient client) throws IOException;

  /**
   * Update RecoveryStorage when InterpreterClient is stopped
   * @param client
   * @throws IOException
   */
  public abstract void onInterpreterClientStop(InterpreterClient client) throws IOException;

  /**
   *
   * It is only called when Zeppelin Server is started.
   *
   * @return
   * @throws IOException
   */
  public abstract Map<String, InterpreterClient> restore() throws IOException;


  /**
   * It is called after constructor
   *
   * @throws IOException
   */
  public void init() throws IOException {
    this.restoredClients = restore();
  }

  public InterpreterClient getInterpreterClient(String interpreterGroupId) {
    if (restoredClients.containsKey(interpreterGroupId)) {
      return restoredClients.get(interpreterGroupId);
    } else {
      return null;
    }
  }
}
