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

import java.io.Serializable;

/**
 * Interpreter result template.
 *
 * @author Leemoonsoo
 *
 */
public class InterpreterResult implements Serializable {

  /**
   *  Type of result after code execution.
   *
   * @author Leemoonsoo
   *
   */
  public static enum Code {
    SUCCESS,
    INCOMPLETE,
    ERROR
  }

  /**
   * Type of Data.
   *
   * @author Leemoonsoo
   *
   */
  public static enum Type {
    TEXT,
    HTML,
    ANGULAR,
    TABLE,
    IMG,
    SVG,
    NULL
  }

  Code code;
  Type type;
  String msg;

  public InterpreterResult(Code code) {
    this.code = code;
    this.msg = null;
    this.type = Type.TEXT;
  }

  public InterpreterResult(Code code, String msg) {
    this.code = code;
    this.msg = getData(msg);
    this.type = getType(msg);
  }

  public InterpreterResult(Code code, Type type, String msg) {
    this.code = code;
    this.msg = msg;
    this.type = type;
  }

  /**
   * Magic is like %html %text.
   *
   * @param msg
   * @return
   */
  private String getData(String msg) {
    if (msg == null) {
      return null;
    }

    Type[] types = Type.values();
    for (Type t : types) {
      String magic = "%" + t.name().toLowerCase();
      if (msg.startsWith(magic + " ") || msg.startsWith(magic + "\n")) {
        int magicLength = magic.length() + 1;
        if (msg.length() > magicLength) {
          return msg.substring(magicLength);
        } else {
          return "";
        }
      }
    }

    return msg;
  }


  private Type getType(String msg) {
    if (msg == null) {
      return Type.TEXT;
    }
    Type[] types = Type.values();
    for (Type t : types) {
      String magic = "%" + t.name().toLowerCase();
      if (msg.startsWith(magic + " ") || msg.startsWith(magic + "\n")) {
        return t;
      }
    }
    return Type.TEXT;
  }

  public Code code() {
    return code;
  }

  public String message() {
    return msg;
  }

  public Type type() {
    return type;
  }

  public InterpreterResult type(Type type) {
    this.type = type;
    return this;
  }
}
