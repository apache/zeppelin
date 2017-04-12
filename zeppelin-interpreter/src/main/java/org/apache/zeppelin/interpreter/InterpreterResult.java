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

import java.io.IOException;
import java.io.Serializable;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Interpreter result template.
 */
public class InterpreterResult implements Serializable, JsonSerializable {
  transient Logger logger = LoggerFactory.getLogger(InterpreterResult.class);
  private static final Gson gson = new Gson();

  /**
   *  Type of result after code execution.
   */
  public static enum Code {
    SUCCESS,
    INCOMPLETE,
    ERROR,
    KEEP_PREVIOUS_RESULT
  }

  /**
   * Type of Data.
   */
  public static enum Type {
    TEXT,
    HTML,
    ANGULAR,
    TABLE,
    IMG,
    SVG,
    NULL,
    NETWORK
  }

  Code code;
  List<InterpreterResultMessage> msg = new LinkedList<>();

  public InterpreterResult(Code code) {
    this.code = code;
  }

  public InterpreterResult(Code code, List<InterpreterResultMessage> msgs) {
    this.code = code;
    msg.addAll(msgs);
  }

  public InterpreterResult(Code code, String msg) {
    this.code = code;
    add(msg);
  }

  public InterpreterResult(Code code, Type type, String msg) {
    this.code = code;
    add(type, msg);
  }

  /**
   * Automatically detect %[display_system] directives
   * @param msg
   */
  public void add(String msg) {
    InterpreterOutput out = new InterpreterOutput(null);
    try {
      out.write(msg);
      out.flush();
      this.msg.addAll(out.toInterpreterResultMessage());
      out.close();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }

  }

  public void add(Type type, String data) {
    msg.add(new InterpreterResultMessage(type, data));
  }

  public void add(InterpreterResultMessage interpreterResultMessage) {
    msg.add(interpreterResultMessage);
  }

  public Code code() {
    return code;
  }

  public List<InterpreterResultMessage> message() {
    return msg;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static InterpreterResult fromJson(String json) {
    return gson.fromJson(json, InterpreterResult.class);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    Type prevType = null;
    for (InterpreterResultMessage m : msg) {
      if (prevType != null) {
        sb.append("\n");
        if (prevType == Type.TABLE) {
          sb.append("\n");
        }
      }
      sb.append(m.toString());
      prevType = m.getType();
    }

    return sb.toString();
  }
}
