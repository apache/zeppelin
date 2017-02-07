package org.apache.zeppelin.interpreter;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

public class InterpreterSettingManager {
  private static final Logger Logger = LoggerFactory.getLogger(InterpreterSettingManager.class);

  private final ZeppelinConfiguration zeppelinConfiguration;
  private final Path interpreterBindingPath;

  public InterpreterSettingManager(ZeppelinConfiguration zeppelinConfiguration) {
    this.zeppelinConfiguration = zeppelinConfiguration;

    Paths.get()

    this.interpreterBindingPath = interpreterBindingPath;
  }
}
