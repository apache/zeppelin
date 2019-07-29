package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.kotlin.context.ZeppelinKotlinContext;

public class SparkKotlinContext extends ZeppelinKotlinContext {
  public SparkSession spark;
  public JavaSparkContext sc;
  public SparkZeppelinContext z;

  public SparkKotlinContext(SparkSession spark,
                            JavaSparkContext sc,
                            SparkZeppelinContext z) {
    super(z);
    this.spark = spark;
    this.sc = sc;
    this.z = z;
  }
}
