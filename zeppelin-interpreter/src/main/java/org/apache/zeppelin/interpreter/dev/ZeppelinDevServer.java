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
package org.apache.zeppelin.interpreter.dev;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.dev.DevInterpreter.InterpreterEvent;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interpreter development server
 */
public class ZeppelinDevServer extends
    RemoteInterpreterServer implements InterpreterEvent, InterpreterOutputChangeListener {
  final Logger logger = LoggerFactory.getLogger(ZeppelinDevServer.class);
  public static final int DEFAULT_TEST_INTERPRETER_PORT = 29914;

  DevInterpreter interpreter = null;
  InterpreterOutput out;
  public ZeppelinDevServer(int port) throws TException {
    super(port);
  }

  @Override
  protected Interpreter getInterpreter(String sessionKey, String className) throws TException {
    synchronized (this) {
      InterpreterGroup interpreterGroup = getInterpreterGroup();
      if (interpreterGroup == null) {
        createInterpreter(
            "dev",
            sessionKey,
            DevInterpreter.class.getName(),
            new HashMap<String, String>(),
            "anonymous");

        Interpreter intp = super.getInterpreter(sessionKey, className);
        interpreter = (DevInterpreter) (
            ((LazyOpenInterpreter) intp).getInnerInterpreter());
        interpreter.setInterpreterEvent(this);
        notify();
      }
    }
    return super.getInterpreter(sessionKey, className);
  }

  @Override
  protected InterpreterOutput createInterpreterOutput(
      final String noteId, final String paragraphId) {
    if (out == null) {
      final RemoteInterpreterEventClient eventClient = getEventClient();
      try {
        out = new InterpreterOutput(new InterpreterOutputListener() {
          @Override
          public void onUpdateAll(InterpreterOutput out) {

          }

          @Override
          public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
            eventClient.onInterpreterOutputAppend(noteId, paragraphId, index, new String(line));
          }

          @Override
          public void onUpdate(int index, InterpreterResultMessageOutput out) {
            try {
              eventClient.onInterpreterOutputUpdate(noteId, paragraphId,
                  index, out.getType(), new String(out.toByteArray()));
            } catch (IOException e) {
              logger.error(e.getMessage(), e);
            }
          }
        }, this);
      } catch (IOException e) {
        return null;
      }
    }

    out.clear();
    return out;
  }

  @Override
  public void fileChanged(File file) {
    refresh();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    waitForConnected();
    return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
  }

  public void refresh() {
    interpreter.rerun();
  }

  /**
   * Wait until %dev paragraph is executed and connected to this process
   */
  public void waitForConnected() {
    synchronized (this) {
      while (!isConnected()) {
        try {
          this.wait(10 * 1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public boolean isConnected() {
    return !(interpreter == null || interpreter.getLastInterpretContext() == null);
  }
}
