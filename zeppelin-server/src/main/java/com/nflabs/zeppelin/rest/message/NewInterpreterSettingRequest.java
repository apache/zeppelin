package com.nflabs.zeppelin.rest.message;

import java.util.Map;

import com.nflabs.zeppelin.interpreter.InterpreterOption;

/**
 *  NewInterpreterSetting rest api request message
 *
 */
public class NewInterpreterSettingRequest {
  String name;
  String group;
  InterpreterOption option;
  Map<String, String> properties;

  public NewInterpreterSettingRequest() {

  }

  public String getName() {
    return name;
  }

  public String getGroup() {
    return group;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public InterpreterOption getOption() {
    return option;
  }
}
