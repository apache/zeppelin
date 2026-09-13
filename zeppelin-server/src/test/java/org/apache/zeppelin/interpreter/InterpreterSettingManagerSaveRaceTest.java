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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.apache.zeppelin.storage.ConfigStorage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Dependency downloads write the interpreter settings from a thread per interpreter setting,
 * so {@link InterpreterSettingManager#saveToFile()} runs concurrently. Its snapshot and its
 * write have to stay one step, otherwise a snapshot taken before a setting was added can be
 * written after the save that carried it, and the file loses that setting while it survives
 * in memory until the next restart.
 */
class InterpreterSettingManagerSaveRaceTest extends AbstractInterpreterTest {

  private static final String NEW_SETTING_NAME = "added_during_save";
  /** How long the first writer waits for a second one before it gives up and writes. */
  private static final long HANDOVER_TIMEOUT_MS = 3000;

  @Test
  void testSaveDuringAnotherSaveIsNotOverwritten() throws Exception {
    RecordingConfigStorage storage = new RecordingConfigStorage(zConf);
    InterpreterSettingManager manager = new InterpreterSettingManager(zConf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class),
        mock(ApplicationEventListener.class), storage, pluginManager);
    try {
      storage.observe();

      // The first writer holds its write until a second write has gone through, which is the
      // window an unserialized saveToFile leaves open between its snapshot and its write.
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      CountDownLatch firstWriterDone = new CountDownLatch(1);
      Thread firstWriter = new Thread(() -> {
        try {
          manager.saveToFile();
        } catch (Exception e) {
          errors.add(e);
        } finally {
          firstWriterDone.countDown();
        }
      }, "saveToFile-holder");
      firstWriter.setDaemon(true);
      firstWriter.start();

      assertTrue(storage.firstWriteEntered.await(10, TimeUnit.SECONDS),
          "the first writer never reached the storage");

      // Add a setting while the first writer is inside its write. createNewSetting() mutates
      // the settings and saves them, so its content has to survive.
      InterpreterOption option = new InterpreterOption();
      option.setPerNote("scoped");
      option.setPerUser("scoped");
      Map<String, InterpreterProperty> properties = new HashMap<>();
      properties.put("property_4", new InterpreterProperty("property_4", "value_4"));
      manager.createNewSetting(NEW_SETTING_NAME, "test", new ArrayList<Dependency>(),
          option, properties);

      assertTrue(firstWriterDone.await(30, TimeUnit.SECONDS),
          "the first writer did not finish within the timeout");
      assertTrue(errors.isEmpty(), () -> "A writer threw: " + errors);

      List<Set<String>> writes = storage.writtenSettingNames;
      assertFalse(writes.isEmpty(), "nothing was written");
      assertTrue(writes.get(writes.size() - 1).contains(NEW_SETTING_NAME),
          () -> "the last write dropped " + NEW_SETTING_NAME + ", writes were " + writes);
    } finally {
      manager.close();
    }
  }

  /**
   * Records the interpreter names of every write. The first write is held back until a second
   * one has been recorded, so that a save which is not serialized can overtake it.
   */
  private static class RecordingConfigStorage extends ConfigStorage {
    private final List<Set<String>> writtenSettingNames = new CopyOnWriteArrayList<>();
    private final CountDownLatch firstWriteEntered = new CountDownLatch(1);
    private volatile boolean observing;
    private volatile boolean firstWrite = true;

    RecordingConfigStorage(ZeppelinConfiguration zConf) {
      super(zConf);
    }

    /** Start recording, so that the writes of the constructor are left out. */
    void observe() {
      observing = true;
    }

    @Override
    public void save(InterpreterInfoSaving settingInfos) throws IOException {
      if (!observing) {
        return;
      }
      Set<String> names = new HashSet<>();
      for (InterpreterSetting setting : settingInfos.interpreterSettings.values()) {
        names.add(setting.getName());
      }
      boolean holdForSecondWrite = firstWrite;
      firstWrite = false;
      if (holdForSecondWrite) {
        firstWriteEntered.countDown();
        // Serialized saves make this wait time out, which is the point: the second write
        // cannot start before this one is done, so it lands last and keeps its content.
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HANDOVER_TIMEOUT_MS);
        while (writtenSettingNames.isEmpty() && System.nanoTime() < deadline) {
          try {
            Thread.sleep(20);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
      writtenSettingNames.add(Collections.unmodifiableSet(names));
    }

    @Override
    public InterpreterInfoSaving loadInterpreterSettings() throws IOException {
      return null;
    }

    @Override
    public void save(NotebookAuthorizationInfoSaving authorizationInfoSaving) throws IOException {
      // not used by this test
    }

    @Override
    public NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException {
      return null;
    }

    @Override
    public String loadCredentials() throws IOException {
      return null;
    }

    @Override
    public void saveCredentials(String credentials) throws IOException {
      // not used by this test
    }
  }
}
