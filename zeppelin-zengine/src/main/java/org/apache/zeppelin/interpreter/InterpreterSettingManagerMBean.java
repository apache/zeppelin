package org.apache.zeppelin.interpreter;

import java.util.Set;

public interface InterpreterSettingManagerMBean {
  Set<String> getRunningInterpreters();
}
