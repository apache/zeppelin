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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterpreterOutputChangeWatcherTest implements InterpreterOutputChangeListener {
  private Path tmpDir;
  private File fileChanged;
  private AtomicInteger numChanged;
  private InterpreterOutputChangeWatcher watcher;

  @BeforeEach
  void setUp() throws Exception {
    watcher = new InterpreterOutputChangeWatcher(this);
    watcher.start();

    tmpDir = Files.createTempDirectory("ZeppelinLTest");
    fileChanged = null;
    numChanged = new AtomicInteger(0);
  }

  @AfterEach
  void tearDown() throws Exception {
    watcher.shutdown();
    FileUtils.deleteDirectory(tmpDir.toFile());
  }

  @Test
  void test() throws IOException, InterruptedException {

    assertNull(fileChanged);
    assertEquals(0, numChanged.get());
    // create new file
    File file1 = new File(tmpDir.toFile(), "test1");
    file1.createNewFile();

    File file2 = new File(tmpDir.toFile(), "test2");
    file2.createNewFile();

    watcher.watch(file1);

    FileOutputStream out1 = new FileOutputStream(file1);
    out1.write(1);
    out1.close();

    FileOutputStream out2 = new FileOutputStream(file2);
    out2.write(1);
    out2.close();

    synchronized (this) {
      wait(30 * 1000);
    }

    assertNotNull(fileChanged);
    assertEquals(fileChanged, file1);
    assertTrue(numChanged.get() >= 1, "Changes: " + numChanged.get());
  }


  @Override
  public void fileChanged(File file) {
    fileChanged = file;
    numChanged.incrementAndGet();

    synchronized (this) {
      notifyAll();
    }
  }

}
