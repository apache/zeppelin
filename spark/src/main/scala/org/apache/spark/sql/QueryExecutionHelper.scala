package org.apache.spark.sql

import org.apache.spark.SparkContext
import org.apache.spark.sql.catalyst.expressions.Attribute
class QueryExecutionHelper(@transient val sc: SparkContext) extends SQLContext(sc) {
  def schemaAttributes(rdd: AnyRef):Seq[Attribute] = {
    rdd.getClass().getMethod("queryExecution").invoke(rdd).asInstanceOf[SQLContext#QueryExecution].analyzed.output
  }
}
