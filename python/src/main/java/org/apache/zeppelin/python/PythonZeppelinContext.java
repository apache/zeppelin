/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.python;

import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;

import java.util.List;
import java.util.Map;

/**
 * ZeppelinContext for Python
 */
public class PythonZeppelinContext extends BaseZeppelinContext {
  ConcurrentLinkedQueue<PythonRestApiRequestResponseMessage> restApiReqResQueue
          = new ConcurrentLinkedQueue();

  public PythonZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
    super(hooks, maxResult);
  }

  @Override
  public Map<String, String> getInterpreterClassMap() {
    return null;
  }

  @Override
  public List<Class> getSupportedClasses() {
    return null;
  }

  @Override
  public String showData(Object obj, int maxResult) {
    return null;
  }

  /**
   * method will be invoked by python.
   */
  public void addRestApiHandler(String endpoint) {
    PythonRestApiHandler handler = new PythonRestApiHandler(endpoint, restApiReqResQueue);
    super.addRestApi(endpoint, handler);
  }

  /**
   * method will be invoked by python
   * @return
   */
  public PythonRestApiRequestResponseMessage getNextApiRequestFromQueue() {
    PythonRestApiRequestResponseMessage message = restApiReqResQueue.poll();
    if (message == null) {
      synchronized (restApiReqResQueue) {
        try {
          restApiReqResQueue.wait(1000);
        } catch (InterruptedException e) {
          // nothing to do
        }
      }
      message = restApiReqResQueue.poll();
    }
    return message;
  }
}
