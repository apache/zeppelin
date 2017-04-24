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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watch the change for the development mode support
 */
public class InterpreterOutputChangeWatcher extends Thread {
  Logger logger = LoggerFactory.getLogger(InterpreterOutputChangeWatcher.class);

  private WatchService watcher;
  private final List<File> watchFiles = new LinkedList<>();
  private final Map<WatchKey, File> watchKeys = new HashMap<>();
  private InterpreterOutputChangeListener listener;
  private boolean stop;

  public InterpreterOutputChangeWatcher(InterpreterOutputChangeListener listener)
      throws IOException {
    watcher = FileSystems.getDefault().newWatchService();
    this.listener = listener;
  }

  public void watch(File file) throws IOException {
    String dirString;
    if (file.isFile()) {
      dirString = file.getParentFile().getAbsolutePath();
    } else {
      throw new IOException(file.getName() + " is not a file");
    }

    if (dirString == null) {
      dirString = "/";
    }

    Path dir = FileSystems.getDefault().getPath(dirString);
    logger.info("watch " + dir);
    WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    synchronized (watchKeys) {
      watchKeys.put(key, new File(dirString));
      watchFiles.add(file);
    }
  }

  public void clear() {
    synchronized (watchKeys) {
      for (WatchKey key : watchKeys.keySet()) {
        key.cancel();

      }
      watchKeys.clear();
      watchFiles.clear();
    }
  }

  public void shutdown() throws IOException {
    stop = true;
    clear();
    watcher.close();
  }

  public void run() {
    while (!stop) {
      WatchKey key = null;
      try {
        key = watcher.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException | ClosedWatchServiceException e) {
        break;
      }

      if (key == null) {
        continue;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == OVERFLOW) {
          continue;
        }
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path filename = ev.context();
        // search for filename
        synchronized (watchKeys) {
          for (File f : watchFiles) {
            if (f.getName().compareTo(filename.toString()) == 0) {
              File changedFile;
              if (filename.isAbsolute()) {
                changedFile = new File(filename.toString());
              } else {
                changedFile = new File(watchKeys.get(key), filename.toString());
              }
              logger.info("File change detected " + changedFile.getAbsolutePath());
              if (listener != null) {
                listener.fileChanged(changedFile);
              }
            }
          }
        }
      }

      boolean valid = key.reset();
      if (!valid) {
        break;
      }
    }
  }
}
