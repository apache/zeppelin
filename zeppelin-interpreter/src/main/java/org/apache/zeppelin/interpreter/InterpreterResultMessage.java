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
 * Interpreter result message
 */
public class InterpreterResultMessage implements Serializable {
  private final InterpreterResult.Type type;
  private final String data;

  public InterpreterResultMessage(InterpreterResult.Type type, String data) {
    this.type = type;
    this.data = data;
  }

  public InterpreterResult.Type getType() {
    return type;
  }

  public String getData() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    InterpreterResultMessage that = (InterpreterResultMessage) o;

    if (type != that.type) return false;
    return data.equals(that.data);
  }

  @Override
  public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + data.hashCode();
    return result;
  }

  public String toString() {
    return "%" + type.name().toLowerCase() + " " + data;
  }
}
