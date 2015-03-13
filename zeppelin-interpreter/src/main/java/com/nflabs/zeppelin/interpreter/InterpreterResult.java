package com.nflabs.zeppelin.interpreter;

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
