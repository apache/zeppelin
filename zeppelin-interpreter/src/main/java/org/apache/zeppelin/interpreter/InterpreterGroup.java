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

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.apache.log4j.Logger;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;

/**
 * InterpreterGroup is list of interpreters in the same group.
 * And unit of interpreter instantiate, restart, bind, unbind.
 */
public class InterpreterGroup extends LinkedList<Interpreter>{
  String id;

  AngularObjectRegistry angularObjectRegistry;

  public InterpreterGroup(String id) {
    this.id = id;
  }

  public InterpreterGroup() {
    getId();
  }

  private static String generateId() {
    return "InterpreterGroup_" + System.currentTimeMillis() + "_"
           + new Random().nextInt();
  }

  public String getId() {
    synchronized (this) {
      if (id == null) {
        id = generateId();
      }
      return id;
    }
  }

  public Properties getProperty() {
    Properties p = new Properties();
    for (Interpreter intp : this) {
      p.putAll(intp.getProperty());
    }
    return p;
  }

  public AngularObjectRegistry getAngularObjectRegistry() {
    return angularObjectRegistry;
  }

  public void setAngularObjectRegistry(AngularObjectRegistry angularObjectRegistry) {
    this.angularObjectRegistry = angularObjectRegistry;
  }

  public void close() {
    List<Thread> closeThreads = new LinkedList<Thread>();

    for (final Interpreter intp : this) {
      Thread t = new Thread() {
        public void run() {
          intp.close();
        }
      };

      t.start();
      closeThreads.add(t);
    }

    for (Thread t : closeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Logger logger = Logger.getLogger(InterpreterGroup.class);
        logger.error("Can't close interpreter", e);
      }
    }
  }

  public void destroy() {
    List<Thread> destroyThreads = new LinkedList<Thread>();

    for (final Interpreter intp : this) {
      Thread t = new Thread() {
        public void run() {
          intp.destroy();
        }
      };

      t.start();
      destroyThreads.add(t);
    }

    for (Thread t : destroyThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Logger logger = Logger.getLogger(InterpreterGroup.class);
        logger.error("Can't close interpreter", e);
      }
    }
  }

  public Interpreter getInterpreterByName(String interpreterName) {
    Interpreter interpreter = null;
    for (Interpreter intp : this) {
      RegisteredInterpreter regIntp = Interpreter
          .findRegisteredInterpreterByClassName(intp.getClassName());
      if (regIntp.getName().equals(interpreterName)) {
        interpreter = intp;
        break;
      }
    }
    return interpreter;
  }

  public void bringDefaultToFront() {
    String defaultInterpreterName = getProperty().getProperty("zeppelin.default.interpreter");
    Logger logger = Logger.getLogger(InterpreterGroup.class);
    logger.info("Default interpreter name is " + defaultInterpreterName);
    if (defaultInterpreterName != null && defaultInterpreterName.trim().length() > 0) {
      Interpreter defaultInterpreter = getInterpreterByName(defaultInterpreterName);
      //if there is a default interpreter, remove it and insert it at the head
      if (defaultInterpreter != null && defaultInterpreter != getFirst()) {
        logger.info("Default interpreter found. Moving to front of list");
        remove(defaultInterpreter);
        addFirst(defaultInterpreter);
      }
    }
  }
}
