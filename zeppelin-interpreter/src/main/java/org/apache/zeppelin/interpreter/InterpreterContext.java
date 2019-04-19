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

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.serving.RestApiHandler;
import org.apache.zeppelin.serving.RestApiServer;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.util.HashMap;
import java.util.Map;

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
  private String noteName;
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
  private String interpreterClassName;
  private Map<String, Integer> progressMap;
  private Map<String, String> localProperties = new HashMap<>();
  private RemoteInterpreterEventClient intpEventClient;
  private RestApiServer restApiServer;

  /**
   * Builder class for InterpreterContext
   */
  public static class Builder {
    private InterpreterContext context;

    public Builder() {
      context = new InterpreterContext();
    }

    public Builder setNoteId(String noteId) {
      context.noteId = noteId;
      return this;
    }

    public Builder setNoteName(String noteName) {
      context.noteName = noteName;
      return this;
    }

    public Builder setParagraphId(String paragraphId) {
      context.paragraphId = paragraphId;
      return this;
    }

    public Builder setInterpreterClassName(String intpClassName) {
      context.interpreterClassName = intpClassName;
      return this;
    }

    public Builder setAngularObjectRegistry(AngularObjectRegistry angularObjectRegistry) {
      context.angularObjectRegistry = angularObjectRegistry;
      return this;
    }

    public Builder setResourcePool(ResourcePool resourcePool) {
      context.resourcePool = resourcePool;
      return this;
    }

    public Builder setReplName(String replName) {
      context.replName = replName;
      return this;
    }

    public Builder setAuthenticationInfo(AuthenticationInfo authenticationInfo) {
      context.authenticationInfo = authenticationInfo;
      return this;
    }

    public Builder setConfig(Map<String, Object> config) {
      context.config = config;
      return this;
    }

    public Builder setGUI(GUI gui) {
      context.gui = gui;
      return this;
    }

    public Builder setNoteGUI(GUI noteGUI) {
      context.noteGui = noteGUI;
      return this;
    }

    public Builder setInterpreterOut(InterpreterOutput out) {
      context.out = out;
      return this;
    }

    public Builder setIntpEventClient(RemoteInterpreterEventClient intpEventClient) {
      context.intpEventClient = intpEventClient;
      return this;
    }

    public Builder setProgressMap(Map<String, Integer> progressMap) {
      context.progressMap = progressMap;
      return this;
    }

    public Builder setParagraphText(String paragraphText) {
      context.paragraphText = paragraphText;
      return this;
    }

    public Builder setParagraphTitle(String paragraphTitle) {
      context.paragraphTitle = paragraphTitle;
      return this;
    }

    public Builder setLocalProperties(Map<String, String> localProperties) {
      context.localProperties = localProperties;
      return this;
    }

    public Builder setRestApiServer(RestApiServer restApiServer) {
      context.restApiServer = restApiServer;
      return this;
    }

    public InterpreterContext build() {
      InterpreterContext.set(context);
      return context;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private InterpreterContext() {

  }


  public String getNoteId() {
    return noteId;
  }

  public String getNoteName() {
    return noteName;
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

  public Map<String, String> getLocalProperties() {
    return localProperties;
  }

  public String getStringLocalProperty(String key, String defaultValue) {
    return localProperties.getOrDefault(key, defaultValue);
  }

  public int getIntLocalProperty(String key, int defaultValue) {
    return Integer.parseInt(localProperties.getOrDefault(key, defaultValue + ""));
  }

  public long getLongLocalProperty(String key, int defaultValue) {
    return Long.parseLong(localProperties.getOrDefault(key, defaultValue + ""));
  }

  public double getDoubleLocalProperty(String key, double defaultValue) {
    return Double.parseDouble(localProperties.getOrDefault(key, defaultValue + ""));
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

  public String getInterpreterClassName() {
    return interpreterClassName;
  }

  public void setInterpreterClassName(String className) {
    this.interpreterClassName = className;
  }

  public RemoteInterpreterEventClient getIntpEventClient() {
    return intpEventClient;
  }

  public void setIntpEventClient(RemoteInterpreterEventClient intpEventClient) {
    this.intpEventClient = intpEventClient;
  }

  public InterpreterOutput out() {
    return out;
  }

  public void addRestApi(String endpoint, RestApiHandler handler) {
    restApiServer.addEndpoint(endpoint, handler);
    this.intpEventClient.addRestApi(noteId, endpoint);
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
