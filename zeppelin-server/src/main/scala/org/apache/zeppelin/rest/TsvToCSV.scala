package org.apache.zeppelin.rest

import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.{LoggerFactory, Logger}

object TsvToCSV {
  def toCSV(tsvData: String): String = {
    val lines: Array[String] = tsvData.split("\n")

    lines.map { row =>
      row.split("\t").map {
        StringEscapeUtils.escapeCsv
      }.mkString(",") // Flatten column to comma separated string

    }.mkString("\n") // Flatten rows to newline separated string
  }
}
