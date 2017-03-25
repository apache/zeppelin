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

package org.apache.zeppelin.beam;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Beam interpreter
 * 
 */
public class BeamInterpreter extends Interpreter {

  Logger logger = LoggerFactory.getLogger(BeamInterpreter.class);

  public BeamInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {

  }

  @Override
  public void close() {
    File dir = new File(".");
    // delete all .class files created while compilation process
    for (int i = 0; i < dir.list().length; i++) {
      File f = dir.listFiles()[i];
      if (f.getAbsolutePath().endsWith(".class")) {
        f.delete();
      }
    }
  }

  @Override
  public InterpreterResult interpret(String code, InterpreterContext context) {

    // choosing new name to class containing Main method
    String generatedClassName = "C" + UUID.randomUUID().toString().replace("-", "");

    try {
      String res = StaticRepl.execute(generatedClassName, code);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, res);
    } catch (Exception e) {
      logger.error("Exception in Interpreter while interpret", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());

    }

  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return Collections.emptyList();
  }

}
