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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * InterpreterOutput is OutputStream that supposed to print content on notebook
 * in addition to InterpreterResult which used to return from Interpreter.interpret().
 */
public class InterpreterOutput extends OutputStream {
  private final List<Object> outList = new LinkedList<Object>();
  private InterpreterOutputChangeWatcher watcher;

  public InterpreterOutput() {
    clear();
  }

  public InterpreterOutput(InterpreterOutputChangeListener listener) throws IOException {
    clear();
    watcher = new InterpreterOutputChangeWatcher(listener);
    watcher.start();
  }

  public void clear() {
    synchronized (outList) {
      outList.clear();
      if (watcher != null) {
        watcher.clear();
      }
    }
  }

  @Override
  public void write(int b) throws IOException {
    synchronized (outList) {
      outList.add(b);
    }
  }

  @Override
  public void write(byte [] b) throws IOException {
    synchronized (outList) {
      outList.add(b);
    }
  }

  @Override
  public void write(byte [] b, int off, int len) throws IOException {
    synchronized (outList) {
      byte[] buf = new byte[len];
      System.arraycopy(b, off, buf, 0, len);
      outList.add(buf);
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
    if ("file".equals(url.getProtocol())) {
      write(new File(url.getPath()));
    } else {
      outList.add(url);
    }
  }

  public void writeResource(String resourceName) throws IOException {
    // search file under resource dir first for dev mode
    File mainResource = new File("./src/main/resources/" + resourceName);
    File testResource = new File("./src/test/resources/" + resourceName);
    if (mainResource.isFile()) {
      write(mainResource);
    } else if (testResource.isFile()) {
      write(testResource);
    } else {
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
  }

  public byte[] toByteArray() throws IOException {
    return toByteArray(false);
  }

  public byte[] toByteArray(boolean clear) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<Object> all = new LinkedList<Object>();

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

    if (clear) {
      clear();
    }

    out.close();
    return out.toByteArray();
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
    if (watcher != null) {
      watcher.clear();
      watcher.shutdown();
    }
  }
}
