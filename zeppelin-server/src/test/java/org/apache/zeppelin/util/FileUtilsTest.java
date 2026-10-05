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

package org.apache.zeppelin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

  private static final int WRITERS = 16;
  private static final int WRITES_PER_WRITER = 300;

  /**
   * Several threads write the same file, the way the interpreter settings are written when
   * more than one interpreter finishes downloading its dependencies. A replace that is not
   * atomic lets one of the moves fail with NoSuchFileException and leaves its temp file in
   * the destination directory.
   */
  @Test
  void testConcurrentWritesToTheSameFileSucceed(@TempDir Path tempDir) throws Exception {
    File target = tempDir.resolve("interpreter.json").toFile();
    ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
    CountDownLatch start = new CountDownLatch(1);
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    try {
      for (int writer = 0; writer < WRITERS; writer++) {
        final int writerId = writer;
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < WRITES_PER_WRITER; i++) {
              FileUtils.atomicWriteToFile("{\"writer\":" + writerId + ",\"write\":" + i + "}",
                  target);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (Exception e) {
            failures.add(e);
          }
        });
      }
      start.countDown();
      pool.shutdown();
      assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "the writers did not finish");
    } finally {
      pool.shutdownNow();
    }

    assertTrue(failures.isEmpty(), () -> "writing the file failed: " + failures);
    File[] leftovers = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".tmp"));
    assertEquals(0, leftovers == null ? 0 : leftovers.length,
        "a failed move left its temp file behind");
    assertTrue(FileUtils.readFromFile(target).startsWith("{\"writer\":"),
        "the file does not hold a complete write");
  }
}
