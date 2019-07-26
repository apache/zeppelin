package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.zeppelin.kotlin.ExecutionContext;

public class KotlinSparkExecutionContext extends ExecutionContext {
  public Object spark;
  public JavaSparkContext sc;
  public SparkZeppelinContext z;

  public KotlinSparkExecutionContext(Object spark,
                                     JavaSparkContext sc,
                                     SparkZeppelinContext z) {
    super("HELLO SPARK");
    this.spark = spark;
    this.sc = sc;
    this.z = z;
  }
}
