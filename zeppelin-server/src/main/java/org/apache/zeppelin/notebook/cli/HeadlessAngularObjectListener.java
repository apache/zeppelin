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

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless implementation of {@link AngularObjectRegistryListener}. There is no UI to broadcast
 * angular object changes to in a headless run, so this only logs at debug level.
 */
public class HeadlessAngularObjectListener implements AngularObjectRegistryListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HeadlessAngularObjectListener.class);

  @Override
  public void onAddAngularObject(String interpreterGroupId, AngularObject angularObject) {
    LOGGER.debug("Angular object added in group {}: {}", interpreterGroupId,
        angularObject.getName());
  }

  @Override
  public void onUpdateAngularObject(String interpreterGroupId, AngularObject angularObject) {
    LOGGER.debug("Angular object updated in group {}: {}", interpreterGroupId,
        angularObject.getName());
  }

  @Override
  public void onRemoveAngularObject(String interpreterGroupId, AngularObject angularObject) {
    LOGGER.debug("Angular object removed in group {}: {}", interpreterGroupId,
        angularObject.getName());
  }
}
