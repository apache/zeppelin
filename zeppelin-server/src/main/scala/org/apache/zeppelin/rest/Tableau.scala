package org.apache.zeppelin.rest

object Tableau {
  /**
   * Turns TSV data into a
   *
   * @param data
   * @return
   */
  def buildWDCResult(data: String, exportName: String): String ={
    val lines = data.split("\n")
    val header = lines.head.split("\t")

    val (columnHeaderJS, columnTypesJS) = makeColumnHeaderJavascript(header)
    val tableDataJS: String = makeDataTableJavascript(lines.drop(1), header)

    pageTemplate.replace("HEADER_NAMES", columnHeaderJS)
                .replace("HEADER_TYPES", columnTypesJS)
                .replace("DATA_RESULTS", tableDataJS)
                .replace("EXPORT_NAME", exportName)
  }

  private def makeColumnHeaderJavascript(header: Array[String]): (String, String) = {
    val headerNames = "[" + header.map(header => s"'$header'").mkString(",") + "];\n"

    // For simplicity just assume everything is of type string;
    // this can be overriden in the Tableau UI
    val headerTypes = "[" + header.map(header => "'string'").mkString(",") + "];\n"

    (headerNames, headerTypes)
  }

  /**
   * Take a TSV / newline delimited string and return a bunch of:
   *
   * @param dataTable
   * @param header
   * @return
   */
  private def makeDataTableJavascript(dataTable: Array[String], header: Array[String]): String = {
    dataTable.map { row =>
      val json =
      row.split("\t").zipWithIndex.map { case (column, columnIndex) =>
        val headerName = header(columnIndex)
        s"'$headerName': '$column'"
      }.mkString(",")

      s"dtr.push({$json});"
    }.mkString("\n")
  }

  private val pageTemplate: String = """
    <html>
      <meta http-equiv="Cache-Control" content="no-store" />
      <head>
        <title>Stock Quote Connector-Tutorial</title>
        <script src="http://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js" type="text/javascript"></script>
        <script src="https://online.tableau.com/javascripts/api/tableauwdc-1.1.0.js" type="text/javascript"></script>
        <script type="text/javascript">
          "use strict";

          $(document).ready(function() {
          var myConnector = tableau.makeConnector();

          myConnector.getColumnHeaders = function() {
            var fieldNames =  HEADER_NAMES;
            var fieldTypes = HEADER_TYPES;
            tableau.headersCallback(fieldNames, fieldTypes);
          }

          myConnector.getTableData = function(lastRecordToken) {
            var dtr = [];
            DATA_RESULTS

            tableau.dataCallback(dtr, dtr.length.toString(), false);
          }

          tableau.registerConnector(myConnector);
          myConnector.init = function() {
            tableau.connectionName = "EXPORT_NAME";
            tableau.initCallback();
            tableau.submit();
          };
          });
        </script>
      </head>
      <body>
      </body>
    </html>
  """
}
