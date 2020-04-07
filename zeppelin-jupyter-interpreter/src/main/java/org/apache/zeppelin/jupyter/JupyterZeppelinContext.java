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


package org.apache.zeppelin.jupyter;

import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;

import java.util.List;
import java.util.Map;

public class JupyterZeppelinContext extends ZeppelinContext {
  public JupyterZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
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
}
