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

package org.apache.zeppelin.rinterpreter;

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;

import java.net.URL;
import java.util.List;
import java.util.Properties;

/**
 * RRepl is a simple wrapper around RReplInterpreter to handle that Zeppelin prefers
 * to load interpreters through classes defined in Java with static methods that run
 * when the class is loaded.
 *
 */
public class RRepl extends Interpreter implements WrappedInterpreter {
  RReplInterpreter intp;

  public RRepl(Properties properties, Boolean startSpark) {
    super(properties);
    intp = new RReplInterpreter(properties, startSpark);
  }
  public RRepl(Properties properties) {
    this(properties, true);
  }

  public RRepl() {
    this(new Properties());
  }

  @Override
  public void open() throws InterpreterException {
    intp.open();
  }

  @Override
  public void close() throws InterpreterException {
    intp.close();
  }

  @Override
  public InterpreterResult interpret(String s, InterpreterContext interpreterContext)
      throws InterpreterException {
    return intp.interpret(s, interpreterContext);
  }

  @Override
  public void cancel(InterpreterContext interpreterContext) throws InterpreterException {
    intp.cancel(interpreterContext);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return intp.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext interpreterContext) throws InterpreterException {
    return intp.getProgress(interpreterContext);
  }

  @Override
  public List<InterpreterCompletion> completion(String s, int i,
      InterpreterContext interpreterContext) throws InterpreterException {
    List completion = intp.completion(s, i, interpreterContext);
    return completion;
  }

  @Override
  public Interpreter getInnerInterpreter() {
    return intp;
  }

  @Override
  public Scheduler getScheduler() {
    return intp.getScheduler();
  }

  @Override
  public void setProperties(Properties properties) {
    super.setProperties(properties);
    intp.setProperties(properties);
  }

  @Override
  public Properties getProperties() {
    return intp.getProperties();
  }

  @Override
  public String getProperty(String key) {
    return intp.getProperty(key);
  }

  @Override
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    super.setInterpreterGroup(interpreterGroup);
    intp.setInterpreterGroup(interpreterGroup);
  }

  @Override
  public InterpreterGroup getInterpreterGroup() {
    return intp.getInterpreterGroup();
  }

  @Override
  public void setClassloaderUrls(URL[] classloaderUrls) {
    intp.setClassloaderUrls(classloaderUrls);
  }

  @Override
  public URL[] getClassloaderUrls() {
    return intp.getClassloaderUrls();
  }
}
