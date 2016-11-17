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

package org.apache.zeppelin.display;

import java.io.Serializable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.zeppelin.display.Input.ParamOption;

/**
 * Settings of a form.
 */
public class GUI implements Serializable {

  Map<String, Object> params = new HashMap<>(); // form parameters from client
  Map<String, Input> forms = new TreeMap<>(); // form configuration

  public GUI() {

  }

  public void setParams(Map<String, Object> values) {
    this.params = values;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public Map<String, Input> getForms() {
    return forms;
  }

  public void setForms(Map<String, Input> forms) {
    this.forms = forms;
  }

  public Object input(String id, Object defaultValue) {
    // first find values from client and then use default
    Object value = params.get(id);
    if (value == null) {
      value = defaultValue;
    }

    forms.put(id, new Input(id, defaultValue, "input"));
    return value;
  }

  public Object input(String id) {
    return input(id, "");
  }

  public Object select(String id, Object defaultValue, ParamOption[] options) {
    Object value = params.get(id);
    if (value == null) {
      value = defaultValue;
    }
    forms.put(id, new Input(id, defaultValue, "select", options));
    return value;
  }

  public Collection<Object> checkbox(String id, Collection<Object> defaultChecked,
                                     ParamOption[] options) {
    Collection<Object> checked = (Collection<Object>) params.get(id);
    if (checked == null) {
      checked = defaultChecked;
    }
    forms.put(id, new Input(id, defaultChecked, "checkbox", options));
    Collection<Object> filtered = new LinkedList<>();
    for (Object o : checked) {
      if (isValidOption(o, options)) {
        filtered.add(o);
      }
    }
    return filtered;
  }

  private boolean isValidOption(Object o, ParamOption[] options) {
    for (ParamOption option : options) {
      if (o.equals(option.getValue())) {
        return true;
      }
    }
    return false;
  }

  public void clear() {
    this.forms = new TreeMap<>();
  }
}
