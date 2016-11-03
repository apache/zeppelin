package org.apache.zeppelin.livy;

import java.util.Properties;

/**
 * Livy PySpark3 interpreter for Zeppelin.
 */
public class LivyPySpark3Interpreter extends LivyPySparkInterpreter {

  public LivyPySpark3Interpreter(Properties property) {
    super(property);
    kind = "pyspark3";
  }
}
