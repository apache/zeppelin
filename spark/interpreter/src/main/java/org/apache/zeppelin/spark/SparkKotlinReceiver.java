package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.kotlin.context.ZeppelinKotlinReceiver;

public class SparkKotlinReceiver extends ZeppelinKotlinReceiver {
  public SparkSession spark;
  public JavaSparkContext sc;
  public SQLContext sqlContext;
  public SparkZeppelinContext z;

  public SparkKotlinReceiver(SparkSession spark,
                             JavaSparkContext sc,
                             SQLContext sqlContext,
                             SparkZeppelinContext z) {
    super(z);
    this.spark = spark;
    this.sc = sc;
    this.sqlContext = sqlContext;
    this.z = z;
  }
}
