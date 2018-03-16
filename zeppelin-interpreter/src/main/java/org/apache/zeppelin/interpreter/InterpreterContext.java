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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.apache.zeppelin.interpreter.remote.RemoteEventClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.resource.ResourcePool;

/**
 * Interpreter context
 */
public class InterpreterContext {
  private static final ThreadLocal<InterpreterContext> threadIC = new ThreadLocal<>();

  public InterpreterOutput out;

  public static InterpreterContext get() {
    return threadIC.get();
  }

  public static void set(InterpreterContext ic) {
    threadIC.set(ic);
  }

  public static void remove() {
    threadIC.remove();
  }

  private String noteId;
  private String replName;
  private String paragraphTitle;
  private String paragraphId;
  private String paragraphText;
  private AuthenticationInfo authenticationInfo;
  private Map<String, Object> config = new HashMap<>();
  private GUI gui = new GUI();
  private GUI noteGui = new GUI();
  private AngularObjectRegistry angularObjectRegistry;
  private ResourcePool resourcePool;
  private List<InterpreterContextRunner> runners = new ArrayList<>();
  private String interpreterClassName;
  private RemoteEventClientWrapper client;
  private RemoteWorksController remoteWorksController;
  private Map<String, Integer> progressMap;

  /**
   * Builder class for InterpreterContext
   */
  public static class Builder {
    private InterpreterContext context = new InterpreterContext();

    public Builder setNoteId(String noteId) {
      context.noteId = noteId;
      return this;
    }

    public Builder setParagraphId(String paragraphId) {
      context.paragraphId = paragraphId;
      return this;
    }

    public Builder setEventClient(RemoteEventClientWrapper client) {
      context.client = client;
      return this;
    }

    public Builder setInterpreterClassName(String intpClassName) {
      context.interpreterClassName = intpClassName;
      return this;
    }

    public Builder setReplName(String replName) {
      context.replName = replName;
      return this;
    }

    public InterpreterContext build() {
      return context;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private InterpreterContext() {

  }

  // visible for testing
  public InterpreterContext(String noteId,
                            String paragraphId,
                            String replName,
                            String paragraphTitle,
                            String paragraphText,
                            AuthenticationInfo authenticationInfo,
                            Map<String, Object> config,
                            GUI gui,
                            GUI noteGui,
                            AngularObjectRegistry angularObjectRegistry,
                            ResourcePool resourcePool,
                            List<InterpreterContextRunner> runners,
                            InterpreterOutput out
                            ) {
    this(noteId, paragraphId, replName, paragraphTitle, paragraphText, authenticationInfo,
        config, gui, noteGui, angularObjectRegistry, resourcePool, runners, out, null, null);
  }

  public InterpreterContext(String noteId,
                            String paragraphId,
                            String replName,
                            String paragraphTitle,
                            String paragraphText,
                            AuthenticationInfo authenticationInfo,
                            Map<String, Object> config,
                            GUI gui,
                            GUI noteGui,
                            AngularObjectRegistry angularObjectRegistry,
                            ResourcePool resourcePool,
                            List<InterpreterContextRunner> runners,
                            InterpreterOutput out,
                            RemoteWorksController remoteWorksController,
                            Map<String, Integer> progressMap
                            ) {
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.replName = replName;
    this.paragraphTitle = paragraphTitle;
    this.paragraphText = paragraphText;
    this.authenticationInfo = authenticationInfo;
    this.config = config;
    this.gui = gui;
    this.noteGui = noteGui;
    this.angularObjectRegistry = angularObjectRegistry;
    this.resourcePool = resourcePool;
    this.runners = runners;
    this.out = out;
    this.remoteWorksController = remoteWorksController;
    this.progressMap = progressMap;
  }

  public InterpreterContext(String noteId,
                            String paragraphId,
                            String replName,
                            String paragraphTitle,
                            String paragraphText,
                            AuthenticationInfo authenticationInfo,
                            Map<String, Object> config,
                            GUI gui,
                            GUI noteGui,
                            AngularObjectRegistry angularObjectRegistry,
                            ResourcePool resourcePool,
                            List<InterpreterContextRunner> contextRunners,
                            InterpreterOutput output,
                            RemoteWorksController remoteWorksController,
                            RemoteInterpreterEventClient eventClient,
                            Map<String, Integer> progressMap) {
    this(noteId, paragraphId, replName, paragraphTitle, paragraphText, authenticationInfo,
        config, gui, noteGui, angularObjectRegistry, resourcePool, contextRunners, output,
        remoteWorksController, progressMap);
    this.client = new RemoteEventClient(eventClient);
  }

  public String getNoteId() {
    return noteId;
  }

  public String getReplName() {
    return replName;
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

  public GUI getNoteGui() {
    return noteGui;
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

  public String getInterpreterClassName() {
    return interpreterClassName;
  }
  
  public void setInterpreterClassName(String className) {
    this.interpreterClassName = className;
  }

  public RemoteEventClientWrapper getClient() {
    return client;
  }

  public void setClient(RemoteEventClientWrapper client) {
    this.client = client;
  }

  public RemoteWorksController getRemoteWorksController() {
    return remoteWorksController;
  }

  public void setRemoteWorksController(RemoteWorksController remoteWorksController) {
    this.remoteWorksController = remoteWorksController;
  }

  public InterpreterOutput out() {
    return out;
  }

  /**
   * Set progress of paragraph manually
   * @param n integer from 0 to 100
   */
  public void setProgress(int n) {
    if (progressMap != null) {
      n = Math.max(n, 0);
      n = Math.min(n, 100);
      progressMap.put(paragraphId, new Integer(n));
    }
  }
}
