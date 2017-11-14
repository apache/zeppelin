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

import org.apache.zeppelin.interpreter.launcher.InterpreterClient;

import java.io.IOException;
import java.util.Map;


/**
 * Interface for Interpreter Process Recovery.
 *
 */
public abstract class RecoveryStorage {

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
   * Restore InterpreterClient when Zeppelin Server is restarted
   * @return
   * @throws IOException
   */
  public abstract Map<String, InterpreterClient> restore() throws IOException;
}
