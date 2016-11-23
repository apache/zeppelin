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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * InterpreterMessageOutputStream
 */
public class InterpreterResultMessageOutput extends OutputStream {
  Logger logger = LoggerFactory.getLogger(InterpreterResultMessageOutput.class);
  private final int NEW_LINE_CHAR = '\n';
  private List<String> resourceSearchPaths;

  ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  private final List<Object> outList = new LinkedList<>();
  private InterpreterOutputChangeWatcher watcher;
  private final InterpreterResultMessageOutputListener flushListener;
  private InterpreterResult.Type type = InterpreterResult.Type.TEXT;
  private boolean firstWrite = true;

  public InterpreterResultMessageOutput(
      InterpreterResult.Type type,
      InterpreterResultMessageOutputListener listener) {
    this.type = type;
    this.flushListener = listener;
  }

  public InterpreterResultMessageOutput(
      InterpreterResult.Type type,
      InterpreterResultMessageOutputListener flushListener,
      InterpreterOutputChangeListener listener) throws IOException {
    this.type = type;
    this.flushListener = flushListener;
    watcher = new InterpreterOutputChangeWatcher(listener);
    watcher.start();
  }

  public InterpreterResult.Type getType() {
    return type;
  }

  public void setType(InterpreterResult.Type type) {
    if (this.type != type) {
      clear();
      this.type = type;
    }
  }

  public void clear() {
    synchronized (outList) {
      buffer.reset();
      outList.clear();
      if (watcher != null) {
        watcher.clear();
      }

      if (flushListener != null) {
        flushListener.onUpdate(this);
      }
    }
  }

  @Override
  public void write(int b) throws IOException {
    synchronized (outList) {
      buffer.write(b);
      if (b == NEW_LINE_CHAR) {
        // first time use of this outputstream.
        if (firstWrite) {
          // clear the output on gui
          if (flushListener != null) {
            flushListener.onUpdate(this);
          }
          firstWrite = false;
        }

        if (isAppendSupported()) {
          flush(true);
        }
      }
    }
  }

  @Override
  public void write(byte [] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte [] b, int off, int len) throws IOException {
    synchronized (outList) {
      for (int i = off; i < len; i++) {
        write(b[i]);
      }
    }
  }

  /**
   * In dev mode, it monitors file and update ZeppelinServer
   * @param file
   * @throws IOException
   */
  public void write(File file) throws IOException {
    outList.add(file);
    if (watcher != null) {
      watcher.watch(file);
    }
  }

  public void write(String string) throws IOException {
    write(string.getBytes());
  }

  /**
   * write contents in the resource file in the classpath
   * @param url
   * @throws IOException
   */
  public void write(URL url) throws IOException {
    outList.add(url);
  }

  public void setResourceSearchPaths(List<String> resourceSearchPaths) {
    this.resourceSearchPaths = resourceSearchPaths;
  }

  public void writeResource(String resourceName) throws IOException {
    // search file under provided paths first, for dev mode
    for (String path : resourceSearchPaths) {
      File res = new File(path + "/" + resourceName);
      if (res.isFile()) {
        write(res);
        return;
      }
    }

    // search from classpath
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = this.getClass().getClassLoader();
    }
    if (cl == null) {
      cl = ClassLoader.getSystemClassLoader();
    }

    write(cl.getResource(resourceName));
  }

  public byte[] toByteArray() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<Object> all = new LinkedList<>();

    synchronized (outList) {
      all.addAll(outList);
    }

    for (Object o : all) {
      if (o instanceof File) {
        File f = (File) o;
        FileInputStream fin = new FileInputStream(f);
        copyStream(fin, out);
        fin.close();
      } else if (o instanceof byte[]) {
        out.write((byte[]) o);
      } else if (o instanceof Integer) {
        out.write((int) o);
      } else if (o instanceof URL) {
        InputStream fin = ((URL) o).openStream();
        copyStream(fin, out);
        fin.close();
      } else {
        // can not handle the object
      }
    }
    out.close();
    return out.toByteArray();
  }

  public InterpreterResultMessage toInterpreterResultMessage() throws IOException {
    return new InterpreterResultMessage(type, new String(toByteArray()));
  }

  private void flush(boolean append) throws IOException {
    synchronized (outList) {
      buffer.flush();
      byte[] bytes = buffer.toByteArray();
      if (bytes != null && bytes.length > 0) {
        outList.add(bytes);
        if (append) {
          if (flushListener != null) {
            flushListener.onAppend(this, bytes);
          }
        } else {
          if (flushListener != null) {
            flushListener.onUpdate(this);
          }
        }
      }
      buffer.reset();
    }
  }

  public void flush() throws IOException {
    flush(isAppendSupported());
  }

  public boolean isAppendSupported() {
    return type == InterpreterResult.Type.TEXT;
  }

  private void copyStream(InputStream in, OutputStream out) throws IOException {
    int bufferSize = 8192;
    byte[] buffer = new byte[bufferSize];

    while (true) {
      int bytesRead = in.read(buffer);
      if (bytesRead == -1) {
        break;
      } else {
        out.write(buffer, 0, bytesRead);
      }
    }
  }

  @Override
  public void close() throws IOException {
    flush();
    if (watcher != null) {
      watcher.clear();
      watcher.shutdown();
    }
  }

  public String toString() {
    try {
      return "%" + type.name().toLowerCase() + " " + new String(toByteArray());
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return "%" + type.name().toLowerCase() + "\n";
    }
  }
}
