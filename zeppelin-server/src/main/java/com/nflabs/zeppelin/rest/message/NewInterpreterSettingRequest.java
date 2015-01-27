package com.nflabs.zeppelin.rest.message;

import java.util.Map;

/**
 *  NewInterpreterSetting rest api request message
 *
 */
public class NewInterpreterSettingRequest {
  String name;
  String group;
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
}
