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
package org.apache.zeppelin.ignite;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;

/**
 * Interpreter for Apache Ignite (http://ignite.incubator.apache.org/)
 */
public class IgniteInterpreter extends Interpreter {
  static {
    Interpreter.register(
        "ignite",
        "ignite",
        IgniteInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("ignite.clientMode", "false", "Client mode. true or false")
            .build());
  }

  private Ignite ignite;

  public IgniteInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    getIgnite();
  }

  @Override
  public void close() {
    synchronized (this) {
      ignite.close();
    }
  }


  public boolean isClientMode() {
    return Boolean.parseBoolean(getProperty("ignite.clientMode"));
  }


  public Ignite getIgnite() {
    synchronized (this) {
      if (ignite == null) {
        IgniteConfiguration conf = new IgniteConfiguration();
        conf.setClientMode(isClientMode());
        ignite = Ignition.start(conf);
      }
      return ignite;
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    throw new InterpreterException("Not implemented yet");
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return new LinkedList<String>();
  }

}
