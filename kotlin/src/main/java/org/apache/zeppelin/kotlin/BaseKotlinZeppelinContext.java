package org.apache.zeppelin.kotlin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;

public class BaseKotlinZeppelinContext extends BaseZeppelinContext {

  private Map<String, String> interpreterClassMap;

  public BaseKotlinZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
    super(hooks, maxResult);
    interpreterClassMap = new HashMap<>();
    interpreterClassMap.put("kotlin", "org.apache.zeppelin.kotlin.KotlinInterpreter");
  }

  @Override
  public Map<String, String> getInterpreterClassMap() {
    return interpreterClassMap;
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
