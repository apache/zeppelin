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

package org.apache.zeppelin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ProcessDataTest {

  private final ProcessData processData = new ProcessData(null, false);

  @Test
  public void detectsMavenDownloadMessages() {
    assertTrue(processData.isDownloadMessage(
        "[INFO] Downloading: https://repo1.maven.org/maven2/foo/foo.jar"));
    assertTrue(processData.isDownloadMessage(
        "[INFO] Downloaded: https://repo1.maven.org/maven2/foo/foo.jar (12 kB at 45 kB/s)"));
    assertTrue(processData.isDownloadMessage("Downloading from central: https://example.com/foo.jar"));
    assertTrue(processData.isDownloadMessage("Downloaded from central: https://example.com/foo.jar"));
  }

  @Test
  public void detectsProgressAndSizeMessages() {
    assertTrue(processData.isDownloadMessage("Progress (1): 45%"));
    assertTrue(processData.isDownloadMessage("progress: 45%"));
    assertTrue(processData.isDownloadMessage("1024/2048 KB"));
    assertTrue(processData.isDownloadMessage("1.5/12 kB"));
  }

  @Test
  public void doesNotFilterOutRealErrorLines() {
    // These lines contain download-shaped figures but are genuine failures and must
    // never be classified as a download message, otherwise they would only ever be
    // reachable via the download-noise path instead of being surfaced as warnings.
    assertFalse(processData.isDownloadMessage("Task failed after writing 1024/2048 MB"));
    assertFalse(processData.isDownloadMessage("Build failed at progress: 50%"));
    assertFalse(processData.isDownloadMessage("ERROR: could not resolve dependency foo:bar:1.0"));
    assertFalse(processData.isDownloadMessage("Compilation error in Main.java"));
  }

  @Test
  public void ignoresBlankOrNullInput() {
    assertFalse(processData.isDownloadMessage(""));
    assertFalse(processData.isDownloadMessage(null));
  }
}