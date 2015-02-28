package com.nflabs.zeppelin.display;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.nflabs.zeppelin.display.Input.ParamOption;

/**
 * Settings of a form.
 *
 * @author Leemoonsoo
 *
 */
public class GUI implements Serializable {

  Map<String, Object> params = new HashMap<String, Object>(); // form parameters from client
  Map<String, Input> forms = new TreeMap<String, Input>(); // form configuration

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

    forms.put(id, new Input(id, defaultValue));
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
    forms.put(id, new Input(id, defaultValue, options));
    return value;
  }

  public void clear() {
    this.forms = new TreeMap<String, Input>();
  }
}
