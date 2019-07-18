package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.zeppelin.kotlin.ExecutionContext;

public class SparkExecutionContext extends ExecutionContext {
  public Object spark;
  public JavaSparkContext sc;

  public SparkExecutionContext(Object spark, JavaSparkContext sc) {
    super("HELLO SPARK");
    this.spark = spark;
    this.sc = sc;
  }
}
