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
package org.apache.zeppelin.serving;

import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.NoteBackgroundTaskManager;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.background.TaskContextStorage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dummy implementation of note serving/test task manager.
 * Used when zeppelin is running in an environment where serving/test is not supported.
 */
public class DummyNoteBackgroundTaskManager extends NoteBackgroundTaskManager {
  private static Logger LOGGER = LoggerFactory.getLogger(DummyNoteBackgroundTaskManager.class);

  public DummyNoteBackgroundTaskManager(ZeppelinConfiguration zConf) {
    super(zConf);
  }

  @Override
  protected TaskContextStorage getTaskContextStorage() {
    LOGGER.info("No note serving task manager is configured");
    return null;
  }

  @Override
  protected NoteBackgroundTask createOrGetBackgroundTask(TaskContext taskContext) {
    LOGGER.info("No note serving task manager is configured");
    return null;
  }
}
