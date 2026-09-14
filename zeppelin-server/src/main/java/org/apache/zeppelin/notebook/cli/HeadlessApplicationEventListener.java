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
package org.apache.zeppelin.notebook.cli;

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless implementation of {@link ApplicationEventListener}. Helium applications are out of
 * scope for headless note execution (no UI to render them into), so this only logs at debug
 * level.
 */
public class HeadlessApplicationEventListener implements ApplicationEventListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(HeadlessApplicationEventListener.class);

  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String appId,
      String output) {
    LOGGER.debug("Helium app {} output append for note {} paragraph {}", appId, noteId,
        paragraphId);
  }

  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index, String appId,
      InterpreterResult.Type type, String output) {
    LOGGER.debug("Helium app {} output updated for note {} paragraph {}", appId, noteId,
        paragraphId);
  }

  @Override
  public void onLoad(String noteId, String paragraphId, String appId, HeliumPackage pkg) {
    LOGGER.debug("Helium app {} loaded for note {} paragraph {}", appId, noteId, paragraphId);
  }

  @Override
  public void onStatusChange(String noteId, String paragraphId, String appId, String status) {
    LOGGER.debug("Helium app {} status changed to {} for note {} paragraph {}", appId, status,
        noteId, paragraphId);
  }
}
