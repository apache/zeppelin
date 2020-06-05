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

import org.apache.zeppelin.tabledata.TableDataUtils;

import java.util.List;

/**
 * Represent the single row interpreter result, usually this is for the sql result.
 * Where you would like to build dashboard for the sql output via just single row. e.g. KPI
 *
 */
public class SingleRowInterpreterResult {

  private String template;
  private List values;
  private InterpreterContext context;

  public SingleRowInterpreterResult(List values, String template, InterpreterContext context) {
    this.values = values;
    this.template = template;
    this.context = context;
  }

  public String toHtml() {
    StringBuilder builder = new StringBuilder();
    builder.append("%html ");
    String outputText = template;
    for (int i = 0; i < values.size(); ++i) {
      outputText = outputText.replace("{" + i + "}", values.get(i).toString());
    }
    builder.append(outputText);
    return builder.toString();
  }

  public String toAngular() {
    StringBuilder builder = new StringBuilder();
    builder.append("%angular ");
    String outputText = template;
    for (int i = 0; i < values.size(); ++i) {
      outputText = outputText.replace("{" + i + "}", "{{value_" + i + "}}");
    }
    builder.append(outputText);
    return builder.toString();
  }

  public void pushAngularObjects() {
    for (int i = 0; i < values.size(); ++i) {
      context.getAngularObjectRegistry().add("value_" + i,
              TableDataUtils.normalizeColumn(values.get(i)),
              context.getNoteId(),
              context.getParagraphId());
    }
  }
}
