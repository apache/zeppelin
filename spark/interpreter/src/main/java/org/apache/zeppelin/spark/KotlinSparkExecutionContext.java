package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.zeppelin.kotlin.ExecutionContext;

public class KotlinSparkExecutionContext extends ExecutionContext {
  public Object spark;
  public JavaSparkContext sc;

  public KotlinSparkExecutionContext(Object spark, JavaSparkContext sc) {
    super("HELLO SPARK");
    this.spark = spark;
    this.sc = sc;
  }
}
