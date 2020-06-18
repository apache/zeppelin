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


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * InterpreterOutput is OutputStream that supposed to print content on notebook
 * in addition to InterpreterResult which used to return from Interpreter.interpret().
 */
public class InterpreterOutput extends OutputStream {
  Logger logger = LoggerFactory.getLogger(InterpreterOutput.class);
  private final int NEW_LINE_CHAR = '\n';
  private final int LINE_FEED_CHAR = '\r';

  private List<InterpreterResultMessageOutput> resultMessageOutputs = new LinkedList<>();
  private InterpreterResultMessageOutput currentOut;
  private List<String> resourceSearchPaths = Collections.synchronizedList(new LinkedList<String>());

  ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  private final InterpreterOutputListener flushListener;
  private final InterpreterOutputChangeListener changeListener;

  private int size = 0;
  private int lastCRIndex = -1;

  // change static var to set interpreter output limit
  // limit will be applied to all InterpreterOutput object.
  // so we can expect the consistent behavior
  public static int limit = Constants.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT;

  public InterpreterOutput(InterpreterOutputListener flushListener) {
    this.flushListener = flushListener;
    changeListener = null;
  }

  public InterpreterOutput(InterpreterOutputListener flushListener,
                           InterpreterOutputChangeListener listener)
      throws IOException {
    this.flushListener = flushListener;
    this.changeListener = listener;
  }

  public void setType(InterpreterResult.Type type) throws IOException {
    InterpreterResultMessageOutput out = null;

    synchronized (resultMessageOutputs) {
      int index = resultMessageOutputs.size();
      InterpreterResultMessageOutputListener listener =
          createInterpreterResultMessageOutputListener(index);

      if (changeListener == null) {
        out = new InterpreterResultMessageOutput(type, listener);
      } else {
        out = new InterpreterResultMessageOutput(type, listener, changeListener);
      }
      out.setResourceSearchPaths(resourceSearchPaths);

      buffer.reset();
      size = 0;
      lastCRIndex = -1;

      if (currentOut != null) {
        currentOut.flush();
      }

      resultMessageOutputs.add(out);
      currentOut = out;
    }
  }

  public InterpreterResultMessageOutputListener createInterpreterResultMessageOutputListener(
      final int index) {

    return new InterpreterResultMessageOutputListener() {
      final int idx = index;

      @Override
      public void onAppend(InterpreterResultMessageOutput out, byte[] line) {
        if (flushListener != null) {
          flushListener.onAppend(idx, out, line);
        }
      }

      @Override
      public void onUpdate(InterpreterResultMessageOutput out) {
        if (flushListener != null) {
          flushListener.onUpdate(idx, out);
        }
      }
    };
  }

  public InterpreterResultMessageOutput getCurrentOutput() {
    synchronized (resultMessageOutputs) {
      return currentOut;
    }
  }

  public InterpreterResultMessageOutput getOutputAt(int index) {
    synchronized (resultMessageOutputs) {
      return resultMessageOutputs.get(index);
    }
  }

  public int size() {
    synchronized (resultMessageOutputs) {
      return resultMessageOutputs.size();
    }
  }

  public void clear() {
    clear(true);
  }

  /**
   *
   * @param sendUpdateToFrontend  Whether send empty result to frontend to clear the paragraph output
   */
  public void clear(boolean sendUpdateToFrontend) {
    size = 0;
    lastCRIndex = -1;
    truncated = false;
    buffer.reset();

    synchronized (resultMessageOutputs) {
      for (InterpreterResultMessageOutput out : resultMessageOutputs) {
        out.clear(sendUpdateToFrontend);
        try {
          out.close();
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }

      // clear all ResultMessages
      resultMessageOutputs.clear();
      currentOut = null;
      startOfTheNewLine = true;
      firstCharIsPercentSign = false;
      if (sendUpdateToFrontend) {
        updateAllResultMessages();
      }
    }
  }

  private void updateAllResultMessages() {
    if (flushListener != null) {
      flushListener.onUpdateAll(this);
    }
  }


  int previousChar = 0;
  boolean startOfTheNewLine = true;
  boolean firstCharIsPercentSign = false;

  boolean truncated = false;

  @Override
  public void write(int b) throws IOException {
    InterpreterResultMessageOutput out;
    if (truncated) {
      return;
    }

    synchronized (resultMessageOutputs) {
      currentOut = getCurrentOutput();

      if (++size > limit) {
        if (b == NEW_LINE_CHAR && currentOut != null) {
          InterpreterResult.Type type = currentOut.getType();
          if (type == InterpreterResult.Type.TEXT || type == InterpreterResult.Type.TABLE) {
            setType(InterpreterResult.Type.HTML);
            getCurrentOutput().write(ResultMessages.getExceedsLimitSizeMessage(limit,
                "ZEPPELIN_INTERPRETER_OUTPUT_LIMIT").getData().getBytes());
            truncated = true;
            return;
          }
        }
      }

      if (b == LINE_FEED_CHAR) {
        if (lastCRIndex == -1) {
          lastCRIndex = size;
        }
        // reset size to index of last carriage return
        size = lastCRIndex;
      }

      if (startOfTheNewLine) {
        if (b == '%') {
          startOfTheNewLine = false;
          firstCharIsPercentSign = true;
          buffer.write(b);
          previousChar = b;
          return;
        } else if (b != NEW_LINE_CHAR) {
          startOfTheNewLine = false;
        }
      }

      if (b == NEW_LINE_CHAR) {
        if (currentOut != null && currentOut.getType() == InterpreterResult.Type.TABLE) {
          if (previousChar == NEW_LINE_CHAR) {
            startOfTheNewLine = true;
            return;
          }
        } else {
          startOfTheNewLine = true;
        }
      }

      boolean flushBuffer = false;
      if (firstCharIsPercentSign) {
        if (b == ' ' || b == NEW_LINE_CHAR || b == '\t') {
          firstCharIsPercentSign = false;
          String displaySystem = buffer.toString();
          for (InterpreterResult.Type type : InterpreterResult.Type.values()) {
            if (displaySystem.equals('%' + type.name().toLowerCase())) {
              // new type detected
              setType(type);
              previousChar = b;
              return;
            }
          }
          // not a defined display system
          flushBuffer = true;
        } else {
          buffer.write(b);
          previousChar = b;
          return;
        }
      }

      out = getCurrentOutputForWriting();

      if (flushBuffer) {
        out.write(buffer.toByteArray());
        buffer.reset();
      }
      out.write(b);
      previousChar = b;
    }
  }

  private InterpreterResultMessageOutput getCurrentOutputForWriting() throws IOException {
    synchronized (resultMessageOutputs) {
      InterpreterResultMessageOutput out = getCurrentOutput();
      if (out == null) {
        // add text type result message
        setType(InterpreterResult.Type.TEXT);
        out = getCurrentOutput();
      }
      return out;
    }
  }

  @Override
  public void write(byte [] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte [] b, int off, int len) throws IOException {
    for (int i = off; i < len; i++) {
      write(b[i]);
    }
  }

  /**
   * In dev mode, it monitors file and update ZeppelinServer
   * @param file
   * @throws IOException
   */
  public void write(File file) throws IOException {
    InterpreterResultMessageOutput out = getCurrentOutputForWriting();
    out.write(file);
  }

  public void write(String string) throws IOException {
    if (string.startsWith("%") && !startOfTheNewLine) {
      // prepend "\n" if it starts with another type of output and startOfTheNewLine is false
      write(("\n" + string).getBytes());
    } else {
      write(string.getBytes());
    }
  }

  /**
   * write contents in the resource file in the classpath
   * @param url
   * @throws IOException
   */
  public void write(URL url) throws IOException {
    InterpreterResultMessageOutput out = getCurrentOutputForWriting();
    out.write(url);
  }

  public void addResourceSearchPath(String path) {
    resourceSearchPaths.add(path);
  }

  public void writeResource(String resourceName) throws IOException {
    InterpreterResultMessageOutput out = getCurrentOutputForWriting();
    out.writeResource(resourceName);
  }

  public List<InterpreterResultMessage> toInterpreterResultMessage() throws IOException {
    List<InterpreterResultMessage> list = new LinkedList<>();
    synchronized (resultMessageOutputs) {
      for (InterpreterResultMessageOutput out : resultMessageOutputs) {
        InterpreterResultMessage msg = out.toInterpreterResultMessage();
        if ((msg.getType() == InterpreterResult.Type.TEXT ||
                msg.getType() == InterpreterResult.Type.HTML) &&
                StringUtils.isBlank(out.toInterpreterResultMessage().getData())) {
          // skip blank text/html, because when print table data we usually need to print '%text \n'
          // first to separate it from previous other kind of data. e.g. z.show(df)
          continue;
        }
        list.add(out.toInterpreterResultMessage());
      }
    }
    return list;
  }

  public void flush() throws IOException {
    InterpreterResultMessageOutput out = getCurrentOutput();
    if (out != null) {
      out.flush();
    }
  }

  public byte[] toByteArray() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    synchronized (resultMessageOutputs) {
      for (InterpreterResultMessageOutput m : resultMessageOutputs) {
        out.write(m.toByteArray());
      }
    }

    return out.toByteArray();
  }

  @Override
  public String toString() {
    try {
      return new String(toByteArray());
    } catch (IOException e) {
      return e.toString();
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (resultMessageOutputs) {
      for (InterpreterResultMessageOutput out : resultMessageOutputs) {
        out.close();
      }
    }
  }
}
