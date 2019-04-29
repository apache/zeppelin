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

package org.apache.zeppelin.shell.terminal.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IOHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(IOHelper.class);

  public static void close(Closeable... closables) {
    for (Closeable closable : closables) {
      try {
        closable.close();
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  public static synchronized void copyLibPty(Path dataDir) throws IOException {
    Path donePath = dataDir.resolve(".DONE");

    if (Files.exists(donePath)) {
      return;
    }

    Set<String> nativeFiles = getNativeFiles();
    for (String nativeFile : nativeFiles) {
      Path nativePath = dataDir.resolve(nativeFile);

      if (Files.notExists(nativePath)) {
        Files.createDirectories(nativePath.getParent());
        InputStream inputStream = IOHelper.class.getResourceAsStream("/" + nativeFile);
        Files.copy(inputStream, nativePath);
        close(inputStream);
      }
    }

    Files.createFile(donePath);
  }

  private static Set<String> getNativeFiles() {
    final Set<String> nativeFiles = new HashSet<>();

    List<String> freebsd = Arrays.asList(
        "libpty/freebsd/x86/libpty.so", "libpty/freebsd/x86_64/libpty.so");
    List<String> linux = Arrays.asList(
        "libpty/linux/x86/libpty.so", "libpty/linux/x86_64/libpty.so");
    List<String> macosx = Arrays.asList(
        "libpty/macosx/x86/libpty.dylib", "libpty/macosx/x86_64/libpty.dylib");
    List<String> win_x86 = Arrays.asList(
        "libpty/win/x86/winpty.dll", "libpty/win/x86/winpty-agent.exe");
    List<String> win_x86_64 = Arrays.asList(
        "libpty/win/x86_64/winpty.dll", "libpty/win/x86_64/winpty-agent.exe",
        "libpty/win/x86_64/cyglaunch.exe");
    List<String> win_xp = Arrays.asList(
        "libpty/win/xp/winpty.dll", "libpty/win/xp/winpty-agent.exe");

    nativeFiles.addAll(freebsd);
    nativeFiles.addAll(linux);
    nativeFiles.addAll(macosx);
    nativeFiles.addAll(win_x86);
    nativeFiles.addAll(win_x86_64);
    nativeFiles.addAll(win_xp);

    return nativeFiles;
  }

}
