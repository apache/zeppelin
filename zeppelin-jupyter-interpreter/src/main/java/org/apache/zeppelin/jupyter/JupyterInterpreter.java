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

import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Interpreter for Jupyter kernel. It can work for any Jupyter kernel as long as the kernel
 * is installed. Specify the kernel name in paragraph properties.
 * Run it via `%jupyter(kernel=ipython)`
 *
 */
public class JupyterInterpreter extends AbstractInterpreter {

  private Map<String, JupyterKernelInterpreter> kernelInterpreterMap = new HashMap<>();

  public JupyterInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public BaseZeppelinContext getZeppelinContext() {
    return new JupyterZeppelinContext(getInterpreterGroup().getInterpreterHookRegistry(), 1000);
  }

  @Override
  protected InterpreterResult internalInterpret(
          String st, InterpreterContext context) throws InterpreterException {
    String kernel = context.getLocalProperties().get("kernel");
    if (kernel == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, "No kernel is specified");
    }
    JupyterKernelInterpreter kernelInterpreter = null;
    synchronized (kernelInterpreterMap) {
      if (kernelInterpreterMap.containsKey(kernel)) {
        kernelInterpreter = kernelInterpreterMap.get(kernel);
      } else {
        kernelInterpreter = new JupyterKernelInterpreter(kernel, properties);
        kernelInterpreter.open();
        kernelInterpreterMap.put(kernel, kernelInterpreter);
      }
    }
    return kernelInterpreter.interpret(st, context);
  }

  @Override
  public void open() throws InterpreterException {
    // do nothing
  }

  @Override
  public void close() throws InterpreterException {
    for (JupyterKernelInterpreter kernelInterpreter : kernelInterpreterMap.values()) {
      kernelInterpreter.close();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    String kernel = context.getLocalProperties().get("kernel");
    if (kernel == null) {
      throw new InterpreterException("No kernel is specified");
    }
    JupyterKernelInterpreter kernelInterpreter = kernelInterpreterMap.get(kernel);
    if (kernelInterpreter == null) {
      throw new InterpreterException("No such interpreter: " + kernel);
    }
    kernelInterpreter.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    String kernel = context.getLocalProperties().get("kernel");
    if (kernel == null) {
      throw new InterpreterException("No kernel is specified");
    }
    JupyterKernelInterpreter kernelInterpreter = kernelInterpreterMap.get(kernel);
    if (kernelInterpreter == null) {
      throw new InterpreterException("No such interpreter: " + kernel);
    }
    return  kernelInterpreter.getProgress(context);
  }

  @Override
  public List<InterpreterCompletion> completion(
          String buf,
          int cursor,
          InterpreterContext context) throws InterpreterException {
    String kernel = context.getLocalProperties().get("kernel");
    if (kernel == null) {
      throw new InterpreterException("No kernel is specified");
    }
    JupyterKernelInterpreter kernelInterpreter = kernelInterpreterMap.get(kernel);
    if (kernelInterpreter == null) {
      throw new InterpreterException("No such interpreter: " + kernel);
    }
    return kernelInterpreter.completion(buf, cursor, context);
  }
}
