package com.nflabs.zeppelin.rest.message;

import java.util.Properties;

import com.nflabs.zeppelin.interpreter.InterpreterOption;

/**
 *
 */
public class UpdateInterpreterSettingRequest {
  InterpreterOption option;
  Properties properties;

  public UpdateInterpreterSettingRequest(InterpreterOption option,
      Properties properties) {
    super();
    this.option = option;
    this.properties = properties;
  }
  public InterpreterOption getOption() {
    return option;
  }
  public Properties getProperties() {
    return properties;
  }


}
