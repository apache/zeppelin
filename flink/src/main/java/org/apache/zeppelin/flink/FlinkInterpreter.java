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

package org.apache.zeppelin.flink;

import org.apache.flink.api.scala.ExecutionEnvironment;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class FlinkInterpreter extends Interpreter {

  private FlinkScalaInterpreter innerIntp;
  private FlinkZeppelinContext z;

  public FlinkInterpreter(Properties properties) {
    super(properties);
    this.innerIntp = new FlinkScalaInterpreter(getProperties());
  }

  @Override
  public void open() throws InterpreterException {
    this.innerIntp.open();

    // bind ZeppelinContext
    int maxRow = Integer.parseInt(getProperty("zeppelin.flink.maxResult", "1000"));
    this.z = new FlinkZeppelinContext(innerIntp.getBatchTableEnviroment(),
        getInterpreterGroup().getInterpreterHookRegistry(), maxRow);
    List<String> modifiers = new ArrayList<>();
    modifiers.add("@transient");
    this.innerIntp.bind("z", z.getClass().getCanonicalName(), z, modifiers);
  }

  @Override
  public void close() throws InterpreterException {
    this.innerIntp.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    this.z.setInterpreterContext(context);
    this.z.setGui(context.getGui());
    this.z.setNoteGui(context.getNoteGui());
    return innerIntp.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {

  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext)
      throws InterpreterException {
    return innerIntp.completion(buf, cursor, interpreterContext);
  }

  FlinkScalaInterpreter getInnerScalaInterpreter() {
    return this.innerIntp;
  }

  ExecutionEnvironment getExecutionEnviroment() {
    return this.innerIntp.getExecutionEnviroment();
  }

  FlinkZeppelinContext getZeppelinContext() {
    return this.z;
  }

}
