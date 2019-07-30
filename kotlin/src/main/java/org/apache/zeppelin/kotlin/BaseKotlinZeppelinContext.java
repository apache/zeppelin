package org.apache.zeppelin.kotlin;

import java.util.List;
import java.util.Map;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;

public class BaseKotlinZeppelinContext extends BaseZeppelinContext {

  public BaseKotlinZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
    super(hooks, maxResult);
  }

  @Override
  public Map<String, String> getInterpreterClassMap() {
    return null;
  }

  @Override
  public List<Class> getSupportedClasses() {
    return null;
  }

  @Override
  public String showData(Object obj, int maxResult) {
    return null;
  }
}
