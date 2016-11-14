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

import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;

/**
 * Interpreter wrapper for lazy initialization
 */
public class LazyOpenInterpreter
    extends Interpreter
    implements WrappedInterpreter {
  private Interpreter intp;
  boolean opened = false;

  public LazyOpenInterpreter(Interpreter intp) {
    super(new Properties());
    this.intp = intp;
  }

  @Override
  public Interpreter getInnerInterpreter() {
    return intp;
  }

  @Override
  public void setProperty(Properties property) {
    intp.setProperty(property);
  }

  @Override
  public Properties getProperty() {
    return intp.getProperty();
  }

  @Override
  public String getProperty(String key) {
    return intp.getProperty(key);
  }

  @Override
  public void open() {
    if (opened == true) {
      return;
    }

    synchronized (intp) {
      if (opened == false) {
        intp.open();
        opened = true;
      }
    }
  }

  @Override
  public void close() {
    synchronized (intp) {
      if (opened == true) {
        intp.close();
        opened = false;
      }
    }
  }

  public boolean isOpen() {
    synchronized (intp) {
      return opened;
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    open();
    return intp.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) {
    open();
    intp.cancel(context);
  }

  @Override
  public FormType getFormType() {
    return intp.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext context) {
    open();
    return intp.getProgress(context);
  }

  @Override
  public Scheduler getScheduler() {
    return intp.getScheduler();
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    open();
    List completion = intp.completion(buf, cursor);
    return completion;
  }

  @Override
  public String getClassName() {
    return intp.getClassName();
  }

  @Override
  public InterpreterGroup getInterpreterGroup() {
    return intp.getInterpreterGroup();
  }

  @Override
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    intp.setInterpreterGroup(interpreterGroup);
  }

  @Override
  public URL [] getClassloaderUrls() {
    return intp.getClassloaderUrls();
  }

  @Override
  public void setClassloaderUrls(URL [] urls) {
    intp.setClassloaderUrls(urls);
  }
  
  @Override
  public void registerHook(String noteId, String event, String cmd) {
    intp.registerHook(noteId, event, cmd);
  }

  @Override
  public void registerHook(String event, String cmd) {
    intp.registerHook(event, cmd);
  }

  @Override
  public String getHook(String noteId, String event) {
    return intp.getHook(noteId, event);
  }

  @Override
  public String getHook(String event) {
    return intp.getHook(event);
  }

  @Override
  public void unregisterHook(String noteId, String event) {
    intp.unregisterHook(noteId, event);
  }

  @Override
  public void unregisterHook(String event) {
    intp.unregisterHook(event);
  }
}
