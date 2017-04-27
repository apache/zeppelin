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
package org.apache.zeppelin.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.zeppelin.interpreter.InterpreterPropertyType;
import org.apache.zeppelin.interpreter.InterpreterPropertyWidget;

/**
 * Utils for widget of interpreter property
 */
public class InterpreterPropertyWidgetUtils {

  private static final List<Widget> availableWidgets = new ArrayList<>();

  static {
    availableWidgets.add(new Widget("textarea", InterpreterPropertyWidget.TEXTAREA.getValue(),
        InterpreterPropertyType.STRING.getValue()));
    availableWidgets.add(new Widget("number", InterpreterPropertyWidget.INPUT.getValue(),
        InterpreterPropertyType.NUMBER.getValue()));
    availableWidgets.add(new Widget("string", InterpreterPropertyWidget.INPUT.getValue(),
        InterpreterPropertyType.STRING.getValue()));
    availableWidgets.add(new Widget("url", InterpreterPropertyWidget.INPUT.getValue(),
        InterpreterPropertyType.URL.getValue()));
    availableWidgets.add(new Widget("password", InterpreterPropertyWidget.PASSWORD.getValue(),
        InterpreterPropertyType.PASSWORD.getValue()));
    availableWidgets.add(new Widget("checkbox", InterpreterPropertyWidget.CHECKBOX.getValue(),
        InterpreterPropertyType.BOOLEAN.getValue()));
  }

  private static class Widget {
    private String id;
    private String widget;
    private String type;

    public Widget(String id, String widget, String type) {
      this.id = id;
      this.widget = widget;
      this.type = type;
    }

    public String getId() {
      return id;
    }

    public String getWidget() {
      return widget;
    }

    public String getType() {
      return type;
    }
  }

  public static List<Widget> getAvailableWidgets() {
    return availableWidgets;
  }
}
