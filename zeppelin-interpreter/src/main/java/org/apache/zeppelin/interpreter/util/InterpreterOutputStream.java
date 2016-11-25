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

package org.apache.zeppelin.interpreter.util;

import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Output Stream integrated with InterpreterOutput.
 *
 * Can be used to channel output from interpreters.
 */
public class InterpreterOutputStream extends LogOutputStream {
  public static Logger logger;
  InterpreterOutput interpreterOutput;

  public InterpreterOutputStream(Logger logger) {
    this.logger = logger;
  }

  public InterpreterOutput getInterpreterOutput() {
    return interpreterOutput;
  }

  public void setInterpreterOutput(InterpreterOutput interpreterOutput) {
    this.interpreterOutput = interpreterOutput;
  }

  @Override
  public void write(int b) throws IOException {
    super.write(b);
    if (interpreterOutput != null) {
      interpreterOutput.write(b);
    }
  }

  @Override
  public void write(byte [] b) throws IOException {
    super.write(b);
    if (interpreterOutput != null) {
      interpreterOutput.write(b);
    }
  }

  @Override
  public void write(byte [] b, int offset, int len) throws IOException {
    super.write(b, offset, len);
    if (interpreterOutput != null) {
      interpreterOutput.write(b, offset, len);
    }
  }

  @Override
  protected void processLine(String s, int i) {
    logger.debug("Interpreter output:" + s);
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (interpreterOutput != null) {
      interpreterOutput.close();
    }
  }


  @Override
  public void flush() throws IOException {
    super.flush();
    if (interpreterOutput != null) {
      interpreterOutput.flush();
    }
  }
}
