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

package org.apache.zeppelin.shell.terminal;

import com.google.gson.Gson;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.Session;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalSession implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSession.class);
  private static final Gson gson = new Gson();

  private static final int DEFAULT_COLUMNS = 20;
  private static final int DEFAULT_ROWS = 10;
  private final AtomicBoolean closed = new AtomicBoolean();

  private PtyProcess process;
  private BufferedReader inputReader;
  private BufferedReader errorReader;
  private BufferedWriter outputWriter;
  private Session webSocketSession;

  private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService readerExecutor = Executors.newFixedThreadPool(2);

  public TerminalSession(Session webSocketSession) throws IOException {
    this(webSocketSession, startProcess());
  }

  TerminalSession(Session webSocketSession, PtyProcess process) {
    this.process = process;
    this.webSocketSession = webSocketSession;
    try {
      webSocketSession.setMaxIdleTimeout(60 * 60 * 1000);
      inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      outputWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
      readerExecutor.execute(() -> printReader(inputReader));
      readerExecutor.execute(() -> printReader(errorReader));
    } catch (RuntimeException e) {
      close();
      throw e;
    }
  }

  private static PtyProcess startProcess() throws IOException {
    boolean isWindows = System.getProperty("os.name").startsWith("Windows");
    String[] termCommand;
    if (isWindows) {
      termCommand = "cmd.exe".split("\\s+");
    } else {
      termCommand = "/bin/bash -i".split("\\s+");
    }

    Map<String, String> envs = new HashMap<>(System.getenv());
    envs.put("TERM", "xterm");

    return new PtyProcessBuilder()
        .setCommand(termCommand)
        .setEnvironment(envs)
        .setInitialColumns(DEFAULT_COLUMNS)
        .setInitialRows(DEFAULT_ROWS)
        .start();
  }

  public void onCommand(String command) {
    if (StringUtils.isEmpty(command)) {
      return;
    }

    try {
      commandExecutor.execute(() -> {
        try {
          outputWriter.write(command);
          outputWriter.flush();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      });
    } catch (RejectedExecutionException e) {
      if (!commandExecutor.isShutdown()) {
        throw e;
      }
    }
  }

  public void onTerminalResize(String columns, String rows) {
    if (Objects.nonNull(columns) && Objects.nonNull(rows)) {
      if (!closed.get()) {
        process.setWinSize(new WinSize(Integer.parseInt(columns), Integer.parseInt(rows)));
      }
    }
  }

  private synchronized void print(String text) throws IOException {
    Map<String, String> map = Map.of("type", "TERMINAL_PRINT", "text", text);
    String message = gson.toJson(map);
    webSocketSession.getBasicRemote().sendText(message);
  }

  private void printReader(BufferedReader bufferedReader) {
    try {
      int nRead;
      char[] data = new char[10 * 1024];

      while ((nRead = bufferedReader.read(data, 0, data.length)) != -1) {
        StringBuilder builder = new StringBuilder(nRead);
        builder.append(data, 0, nRead);
        print(builder.toString());
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    commandExecutor.shutdownNow();
    readerExecutor.shutdownNow();
    destroyProcess(process);
    for (Closeable stream : new Closeable[] {outputWriter, inputReader, errorReader}) {
      if (stream == null) {
        continue;
      }
      try {
        stream.close();
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  private void destroyProcess(PtyProcess process) {
    process.destroy();
    try {
      if (!process.waitFor(5L, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }
}
