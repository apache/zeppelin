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

import java.util.List;
import java.util.Map;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.resource.ResourcePool;

/**
 * Interpreter context
 */
public class InterpreterContext {
  private static final ThreadLocal<InterpreterContext> threadIC =
      new ThreadLocal<InterpreterContext>();
  public final InterpreterOutput out;

  public static InterpreterContext get() {
    return threadIC.get();
  }

  public static void set(InterpreterContext ic) {
    threadIC.set(ic);
  }

  public static void remove() {
    threadIC.remove();
  }

  private final String noteId;
  private final String paragraphTitle;
  private final String paragraphId;
  private final String paragraphText;
  private AuthenticationInfo authenticationInfo;
  private final Map<String, Object> config;
  private GUI gui;
  private AngularObjectRegistry angularObjectRegistry;
  private ResourcePool resourcePool;
  private List<InterpreterContextRunner> runners;

  public InterpreterContext(String noteId,
                            String paragraphId,
                            String paragraphTitle,
                            String paragraphText,
                            AuthenticationInfo authenticationInfo,
                            Map<String, Object> config,
                            GUI gui,
                            AngularObjectRegistry angularObjectRegistry,
                            ResourcePool resourcePool,
                            List<InterpreterContextRunner> runners,
                            InterpreterOutput out
                            ) {
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.paragraphTitle = paragraphTitle;
    this.paragraphText = paragraphText;
    this.authenticationInfo = authenticationInfo;
    this.config = config;
    this.gui = gui;
    this.angularObjectRegistry = angularObjectRegistry;
    this.resourcePool = resourcePool;
    this.runners = runners;
    this.out = out;
  }


  public String getNoteId() {
    return noteId;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public String getParagraphText() {
    return paragraphText;
  }

  public String getParagraphTitle() {
    return paragraphTitle;
  }

  public AuthenticationInfo getAuthenticationInfo() {
    return authenticationInfo;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public GUI getGui() {
    return gui;
  }

  public AngularObjectRegistry getAngularObjectRegistry() {
    return angularObjectRegistry;
  }

  public ResourcePool getResourcePool() {
    return resourcePool;
  }

  public List<InterpreterContextRunner> getRunners() {
    return runners;
  }

}
