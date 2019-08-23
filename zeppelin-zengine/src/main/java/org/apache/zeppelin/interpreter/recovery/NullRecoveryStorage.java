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
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;

import java.io.IOException;
import java.util.Map;


/**
 * RecoveryStorage that do nothing, used when recovery is not enabled.
 *
 */
public class NullRecoveryStorage extends RecoveryStorage {

  public NullRecoveryStorage(ZeppelinConfiguration zConf,
                             InterpreterSettingManager interpreterSettingManager)
      throws IOException {
    super(zConf);
  }

  @Override
  public void onInterpreterClientStart(InterpreterClient client) throws IOException {

  }

  @Override
  public void onInterpreterClientStop(InterpreterClient client) throws IOException {

  }

  @Override
  public Map<String, InterpreterClient> restore() throws IOException {
    return null;
  }
}
