package org.apache.zeppelin.livy;

import java.util.Properties;

/**
 * Base class for PySpark Interpreter
 */
public abstract class LivyPySparkBaseInterpreter extends BaseLivyInterprereter {

  public LivyPySparkBaseInterpreter(Properties property) {
    super(property);
  }

  @Override
  protected String extractAppId() throws LivyException {
    return extractStatementResult(
        interpret("sc.applicationId", null, false, false).message()
            .get(0).getData());
  }

  @Override
  protected String extractWebUIAddress() throws LivyException {
    return extractStatementResult(
        interpret(
            "sc._jsc.sc().ui().get().appUIAddress()", null, false, false)
            .message().get(0).getData());
  }

  /**
   * Extract the eval result of spark shell, e.g. extract application_1473129941656_0048
   * from following:
   * u'application_1473129941656_0048'
   *
   * @param result
   * @return
   */
  private String extractStatementResult(String result) {
    int pos = -1;
    if ((pos = result.indexOf("'")) >= 0) {
      return result.substring(pos + 1, result.length() - 1).trim();
    } else {
      throw new RuntimeException("No result can be extracted from '" + result + "', " +
          "something must be wrong");
    }
  }
}
