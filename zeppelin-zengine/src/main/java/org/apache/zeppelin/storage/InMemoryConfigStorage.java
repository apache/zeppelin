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

package org.apache.zeppelin.storage;

import java.io.IOException;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;

/**
 * Storing config in memory
 * This class is primarily for test cases
 */
public class InMemoryConfigStorage extends ConfigStorage {

  private InterpreterInfoSaving settingInfos;
  private NotebookAuthorizationInfoSaving authorizationInfoSaving;
  private String credentials;

  public InMemoryConfigStorage(ZeppelinConfiguration zConf) {
    super(zConf);
  }

  @Override
  public void save(InterpreterInfoSaving settingInfos) throws IOException {
    this.settingInfos = settingInfos;
  }

  @Override
  public InterpreterInfoSaving loadInterpreterSettings() throws IOException {
    return settingInfos;
  }

  @Override
  public void save(NotebookAuthorizationInfoSaving authorizationInfoSaving) throws IOException {
    this.authorizationInfoSaving = authorizationInfoSaving;
  }

  @Override
  public NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException {
    return authorizationInfoSaving;
  }

  @Override
  public String loadCredentials() throws IOException {
    return credentials;
  }

  @Override
  public void saveCredentials(String credentials) throws IOException {
    this.credentials = credentials;
  }

}