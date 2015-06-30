package org.apache.zeppelin.spark.utils

import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.types.{StructType, StructField}

import scala.collection.JavaConverters._
import scala.io.Source

class SparkSqlCompleter(val sqlContext: SQLContext) {

  private val keywords = Source.fromInputStream(this.getClass.getResourceAsStream("/sparksql.txt")).getLines().toSet

  def completion(buf: String, cursor: Int): java.util.List[String] = {
    import sqlContext.implicits._

    val tables = sqlContext.tables().select('tableName).collect().map(_.getString(0))
    val fields = tables.flatMap(getFields)

    val candidates = (tables ++ fields ++ keywords).toSet
    val prefix = buf.substring(0, cursor - 1).split("\\s").last.toLowerCase
    val offset = if (prefix.contains(".")) prefix.lastIndexOf('.') + 1 else 0
    candidates.filter(_.toLowerCase.startsWith(prefix)).map(_.substring(offset)).toList.asJava
  }

  private def getFields(table: String): Seq[String] = {
    def fieldNames(prefix: Seq[String], fields: Array[StructField]): Seq[String] = {
      fields.flatMap { f =>
        val sub = f.dataType match {
          case StructType(subFields) => fieldNames(prefix :+ f.name, subFields)
          case _ => Seq.empty
        }
        sub :+ (prefix :+ f.name).mkString(".")
      }
    }
    fieldNames(Seq.empty, sqlContext.table(table).schema.fields)
  }

}
