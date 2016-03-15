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
import org.apache.commons.lang3.StringUtils;
import java.util.*;

/**
 * Interpreter result template.
 */
public class InterpreterResult implements Serializable {

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
    Type[] types = type.values();
    TreeMap<Integer, Type> typesLastIndexInMsg = buildIndexMap(msg);
    if (typesLastIndexInMsg.size() == 0) {
      return msg;
    } else {
      Map.Entry<Integer, Type> lastType = typesLastIndexInMsg.firstEntry();
      //add 1 for the % char
      int magicLength = lastType.getValue().name().length() + 1;
      // 1 for the last \n or space after magic
      int subStringPos = magicLength + lastType.getKey() + 1;
      return msg.substring(subStringPos);
    }
  }

  private Type getType(String msg) {
    if (msg == null) {
      return Type.TEXT;
    }
    Type[] types = type.values();
    TreeMap<Integer, Type> typesLastIndexInMsg = buildIndexMap(msg);
    if (typesLastIndexInMsg.size() == 0) {
      return Type.TEXT;
    } else {
      Map.Entry<Integer, Type> lastType = typesLastIndexInMsg.firstEntry();
      return lastType.getValue();
    }
  }

  private int getIndexOfType(String msg, Type t) {
    if (msg == null) {
      return 0;
    }
    String typeString = "%" + t.name().toLowerCase();
    return StringUtils.indexOf(msg, typeString );
  }

  private TreeMap<Integer, Type> buildIndexMap(String msg) {
    int lastIndexOftypes = 0;
    TreeMap<Integer, Type> typesLastIndexInMsg = new TreeMap<Integer, Type>();
    Type[] types = Type.values();
    for (Type t : types) {
      lastIndexOftypes = getIndexOfType(msg, t);
      if (lastIndexOftypes >= 0) {
        typesLastIndexInMsg.put(lastIndexOftypes, t);
      }
    }
    return typesLastIndexInMsg;
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

  public String toString() {
    return "%" + type.name().toLowerCase() + " " + msg;
  }
}
