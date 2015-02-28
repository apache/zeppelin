package com.nflabs.zeppelin.rest.message;

import java.util.Properties;

/**
 *
 */
public class UpdateInterpreterSettingRequest {
  boolean remote;
  Properties properties;

  public UpdateInterpreterSettingRequest(boolean remote,
      Properties properties) {
    super();
    this.remote = remote;
    this.properties = properties;
  }
  public boolean isRemote() {
    return remote;
  }
  public Properties getProperties() {
    return properties;
  }


}
