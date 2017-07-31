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
package org.apache.zeppelin.jupyter.types;

/**
 * Jupyter Output Types.
 */
public enum JupyterOutputType {
  TEXT_PLAIN("text/plain"),
  IMAGE_PNG("image/png"),
  LATEX("text/latex"),
  SVG_XML("image/svg+xml"),
  TEXT_HTML("text/html"),
  APPLICATION_JAVASCRIPT("application/javascript")
  ;

  private final String type;
  private JupyterOutputType(final String type) {
    this.type = type;
  }

  public ZeppelinOutputType getZeppelinType() {
    return Convertor.ToZeppelin.getType(type);
  }

  public static JupyterOutputType getByValue(String value) {
    for (JupyterOutputType type : JupyterOutputType.values()) {
      if (type.toString().equals(value)) {
        return type;
      }
    }
    return JupyterOutputType.TEXT_PLAIN;
  }

  @Override
  public String toString() {
    return type;
  }

  private enum Convertor {
    ToZeppelin;

    public ZeppelinOutputType getType(String typeValue) {
      JupyterOutputType type = JupyterOutputType.getByValue(typeValue);
      ZeppelinOutputType outputType;

      if (JupyterOutputType.TEXT_PLAIN == type) {
        outputType = ZeppelinOutputType.TEXT;
      } else {
        outputType = ZeppelinOutputType.HTML;
      }

      return outputType;
    }
  }
}
