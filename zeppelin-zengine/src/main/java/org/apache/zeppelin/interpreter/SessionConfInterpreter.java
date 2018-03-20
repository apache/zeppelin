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

package org.apache.zeppelin.interpreter;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

public class SessionConfInterpreter extends ConfInterpreter {

  private static Logger LOGGER = LoggerFactory.getLogger(SessionConfInterpreter.class);

  public SessionConfInterpreter(Properties properties,
                                String sessionId,
                                String interpreterGroupId,
                                InterpreterSetting interpreterSetting) {
    super(properties, sessionId, interpreterGroupId, interpreterSetting);
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    try {
      Properties finalProperties = new Properties();
      finalProperties.putAll(this.properties);
      Properties updatedProperties = new Properties();
      updatedProperties.load(new StringReader(st));
      finalProperties.putAll(updatedProperties);
      LOGGER.debug("Properties for Session: " + sessionId + ": " + finalProperties);

      List<Interpreter> interpreters =
          interpreterSetting.getInterpreterGroup(interpreterGroupId).get(sessionId);
      for (Interpreter intp : interpreters) {
        // only check the RemoteInterpreter, ConfInterpreter itself will be ignored here.
        if (intp instanceof RemoteInterpreter) {
          RemoteInterpreter remoteInterpreter = (RemoteInterpreter) intp;
          if (remoteInterpreter.isOpened()) {
            return new InterpreterResult(InterpreterResult.Code.ERROR,
                "Can not change interpreter session properties after this session is started");
          }
          remoteInterpreter.setProperties(finalProperties);
        }
      }
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    } catch (IOException e) {
      LOGGER.error("Fail to update interpreter setting", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
    }
  }
}
