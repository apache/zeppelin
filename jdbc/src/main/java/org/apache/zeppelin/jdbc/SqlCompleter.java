/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.jdbc;

/*
 * This source file is based on code taken from SQLLine 1.0.2 See SQLLine notice in LICENSE
 */

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;
import jline.console.completer.StringsCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * SQL auto complete functionality for the JdbcInterpreter.
 */
public class SqlCompleter extends StringsCompleter {

  private static Logger logger = LoggerFactory.getLogger(SqlCompleter.class);

  /**
   * Delimiter that can split SQL statement in keyword list
   */
  private WhitespaceArgumentDelimiter sqlDelimiter = new WhitespaceArgumentDelimiter() {

    private Pattern pattern = Pattern.compile(",");

    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      return pattern.matcher("" + buffer.charAt(pos)).matches()
              || super.isDelimiterChar(buffer, pos);
    }
  };

  /**
   * Schema completer
   */
  private StringsCompleter schemasCompleter = new StringsCompleter();

  /**
   * Contain different completer with table list for every schema name
   */
  private Map<String, StringsCompleter> tablesCompleters = new HashMap<>();

  /**
   * Contains different completer with column list for every table name
   * Table names store as schema_name.table_name
   */
  private Map<String, StringsCompleter> columnsCompleters = new HashMap<>();

  /**
   * Completer for sql keywords
   */
  private StringsCompleter keywordCompleter = new StringsCompleter();

  @Override
  public int complete(String buffer, int cursor, List<CharSequence> candidates) {

    logger.debug("Complete with buffer = " + buffer + ", cursor = " + cursor);

    // The delimiter breaks the buffer into separate words (arguments), separated by the
    // white spaces.
    ArgumentList argumentList = sqlDelimiter.delimit(buffer, cursor);

    String beforeCursorBuffer = buffer.substring(0,
            Math.min(cursor, buffer.length())).toUpperCase();

    // check what sql is and where cursor is to allow column completion or not
    boolean isColumnAllowed = true;
    if (beforeCursorBuffer.contains("SELECT ") && beforeCursorBuffer.contains(" FROM ")
            && !beforeCursorBuffer.contains(" WHERE "))
      isColumnAllowed = false;

    int complete = completeName(argumentList.getCursorArgument(),
            argumentList.getArgumentPosition(), candidates,
            findAliasesInSQL(argumentList.getArguments()), isColumnAllowed);

    logger.debug("complete:" + complete + ", size:" + candidates.size());

    return complete;
  }

  /**
   * Return list of schema names within the database
   *
   * @param meta metadata from connection to database
   * @param schemaFilter a schema name pattern; must match the schema name
   *        as it is stored in the database; "" retrieves those without a schema;
   *        <code>null</code> means that the schema name should not be used to narrow
   *        the search; supports '%' and '_' symbols; for example "prod_v_%"
   * @return set of all schema names in the database
   */
  private static Set<String> getSchemaNames(DatabaseMetaData meta, String schemaFilter) {
    Set<String> res = new HashSet<>();
    try {
      ResultSet schemas = meta.getSchemas();
      try {
        while (schemas.next()) {
          String schemaName = schemas.getString("TABLE_SCHEM");
          if (schemaFilter.equals("") || schemaFilter == null || schemaName.matches(
                  schemaFilter.replace("_", ".").replace("%", ".*?"))) {
            res.add(schemaName);
          }
        }
      } finally {
        schemas.close();
      }
    } catch (SQLException t) {
      logger.error("Failed to retrieve the schema names", t);
    }
    return res;
  }

  /**
   * Fill two map with list of tables and list of columns
   *
   * @param meta metadata from connection to database
   * @param schemaFilter a schema name pattern; must match the schema name
   *        as it is stored in the database; "" retrieves those without a schema;
   *        <code>null</code> means that the schema name should not be used to narrow
   *        the search; supports '%' and '_' symbols; for example "prod_v_%"
   * @param tables function fills this map, for every schema name adds
   *        set of table names within the schema
   * @param columns function fills this map, for every table name adds set
   *        of columns within the table; table name is in format schema_name.table_name
   */
  private static void fillTableAndColumnNames(DatabaseMetaData meta, String schemaFilter,
                                              Map<String, Set<String>> tables,
                                              Map<String, Set<String>> columns)  {
    tables.clear();
    columns.clear();
    try {
      ResultSet cols = meta.getColumns(meta.getConnection().getCatalog(),
              schemaFilter, "%", "%");
      try {
        while (cols.next()) {
          String schema = cols.getString("TABLE_SCHEM");
          if (schema == null) schema = cols.getString("TABLE_CAT");
          String table = cols.getString("TABLE_NAME");
          String column = cols.getString("COLUMN_NAME");
          if (!isBlank(table)) {
            String schemaTable = schema + "." + table;
            if (!columns.containsKey(schemaTable)) columns.put(schemaTable, new HashSet<String>());
            columns.get(schemaTable).add(column);
            if (!tables.containsKey(schema)) tables.put(schema, new HashSet<String>());
            tables.get(schema).add(table);
          }
        }
      } finally {
        cols.close();
      }
    } catch (Throwable t) {
      logger.error("Failed to retrieve the column name", t);
    }
  }

  public static Set<String> getSqlKeywordsCompletions(Connection connection) throws IOException,
          SQLException {

    // Add the default SQL completions
    String keywords =
            new BufferedReader(new InputStreamReader(
                    SqlCompleter.class.getResourceAsStream("/ansi.sql.keywords"))).readLine();

    Set<String> completions = new TreeSet<>();

    if (null != connection) {
      DatabaseMetaData metaData = connection.getMetaData();

      // Add the driver specific SQL completions
      String driverSpecificKeywords =
              "/" + metaData.getDriverName().replace(" ", "-").toLowerCase() + "-sql.keywords";
      logger.info("JDBC DriverName:" + driverSpecificKeywords);
      try {
        if (SqlCompleter.class.getResource(driverSpecificKeywords) != null) {
          String driverKeywords =
                  new BufferedReader(new InputStreamReader(
                          SqlCompleter.class.getResourceAsStream(driverSpecificKeywords))).readLine();
          keywords += "," + driverKeywords.toUpperCase();
        }
      } catch (Exception e) {
        logger.debug("fail to get driver specific SQL completions for " +
                driverSpecificKeywords + " : " + e, e);
      }


      // Add the keywords from the current JDBC connection
      try {
        keywords += "," + metaData.getSQLKeywords();
      } catch (Exception e) {
        logger.debug("fail to get SQL key words from database metadata: " + e, e);
      }
      try {
        keywords += "," + metaData.getStringFunctions();
      } catch (Exception e) {
        logger.debug("fail to get string function names from database metadata: " + e, e);
      }
      try {
        keywords += "," + metaData.getNumericFunctions();
      } catch (Exception e) {
        logger.debug("fail to get numeric function names from database metadata: " + e, e);
      }
      try {
        keywords += "," + metaData.getSystemFunctions();
      } catch (Exception e) {
        logger.debug("fail to get system function names from database metadata: " + e, e);
      }
      try {
        keywords += "," + metaData.getTimeDateFunctions();
      } catch (Exception e) {
        logger.debug("fail to get time date function names from database metadata: " + e, e);
      }

      // Set all keywords to lower-case versions
      keywords = keywords.toLowerCase();

    }

    StringTokenizer tok = new StringTokenizer(keywords, ", ");
    while (tok.hasMoreTokens()) {
      completions.add(tok.nextToken());
    }

    return completions;
  }

  /**
   * Initializes local schema completers from list of schema names
   *
   * @param schemas set of schema names
   */
  private void initSchemas(Set<String> schemas) {
    schemasCompleter = new StringsCompleter(new TreeSet<>(schemas));
  }

  /**
   * Initializes local table completers from list of table name
   *
   * @param tables for every schema name there is a set of table names within the schema
   */
  private void initTables(Map<String, Set<String>> tables) {
    tablesCompleters.clear();
    for (Map.Entry<String, Set<String>> entry : tables.entrySet()) {
      tablesCompleters.put(entry.getKey(), new StringsCompleter(new TreeSet<>(entry.getValue())));
    }
  }

  /**
   * Initializes local column completers from list of column names
   *
   * @param columns for every table name there is a set of columns within the table;
   *        table name is in format schema_name.table_name
   */
  private void initColumns(Map<String, Set<String>> columns) {
    columnsCompleters.clear();
    for (Map.Entry<String, Set<String>> entry : columns.entrySet()) {
      columnsCompleters.put(entry.getKey(), new StringsCompleter(new TreeSet<>(entry.getValue())));
    }
  }

  /**
   * Initializes all local completers
   *
   * @param schemas set of schema names
   * @param tables for every schema name there is a set of table names within the schema
   * @param columns for every table name there is a set of columns within the table;
   *        table name is in format schema_name.table_name
   * @param keywords set with sql keywords
   */
  public void init(Set<String> schemas, Map<String, Set<String>> tables,
                   Map<String, Set<String>> columns, Set<String> keywords) {
    initSchemas(schemas);
    initTables(tables);
    initColumns(columns);
    keywordCompleter = new StringsCompleter(keywords);
  }

  /**
   * Initializes all local completers from database connection
   *
   * @param connection database connection
   * @param schemaFilter a schema name pattern; must match the schema name
   *        as it is stored in the database; "" retrieves those without a schema;
   *        <code>null</code> means that the schema name should not be used to narrow
   *        the search; supports '%' and '_' symbols; for example "prod_v_%"
   */
  public void initFromConnection(Connection connection, String schemaFilter) {

    try {
      Map<String, Set<String>> tables = new HashMap<>();
      Map<String, Set<String>> columns = new HashMap<>();
      Set<String> schemas = new HashSet<>();
      Set<String> keywords = getSqlKeywordsCompletions(connection);
      if (connection != null) {
        schemas = getSchemaNames(connection.getMetaData(), schemaFilter);
        if (schemas.size() == 0) schemas.add(connection.getCatalog());
        fillTableAndColumnNames(connection.getMetaData(), schemaFilter, tables, columns);
      }
      init(schemas, tables, columns, keywords);
      logger.info("Completer initialized with " + schemas.size() + " schemas, " +
              columns.size() + " tables and " + keywords.size() + " keywords");

    } catch (SQLException | IOException e) {
      logger.error("Failed to update the metadata conmpletions", e);
    }
  }

  /**
   * Find aliases in sql command
   *
   * @param sqlArguments sql command divided on arguments
   * @return for every alias contains table name in format schema_name.table_name
   */
  public Map<String, String> findAliasesInSQL(String[] sqlArguments) {
    Map<String, String> res = new HashMap<>();
    for (int i = 0; i < sqlArguments.length - 1; i++) {
      if (columnsCompleters.keySet().contains(sqlArguments[i]) &&
              sqlArguments[i + 1].matches("[a-zA-Z]+")) {
        res.put(sqlArguments[i + 1], sqlArguments[i]);
      }
    }
    return res;
  }

  /**
   * Complete buffer in case it is a keyword
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeKeyword(String buffer, int cursor, List<CharSequence> candidates) {
    return keywordCompleter.complete(buffer, cursor, candidates);
  }

  /**
   * Complete buffer in case it is a schema name
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeSchema(String buffer, int cursor, List<CharSequence> candidates) {
    return schemasCompleter.complete(buffer, cursor, candidates);
  }

  /**
   * Complete buffer in case it is a table name
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeTable(String schema, String buffer, int cursor,
                            List<CharSequence> candidates) {
    // Wrong schema
    if (!tablesCompleters.containsKey(schema))
      return -1;
    else
      return tablesCompleters.get(schema).complete(buffer, cursor, candidates);
  }

  /**
   * Complete buffer in case it is a column name
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeColumn(String schema, String table, String buffer, int cursor,
                             List<CharSequence> candidates) {
    // Wrong schema or wrong table
    if (!tablesCompleters.containsKey(schema) ||
            !columnsCompleters.containsKey(schema + "." + table))
      return -1;
    else
      return columnsCompleters.get(schema + "." + table).complete(buffer, cursor, candidates);
  }

  /**
   * Complete buffer with a single name. Function will decide what it is:
   * a schema, a table of a column or a keyword
   *
   * @param aliases for every alias contains table name in format schema_name.table_name
   * @param isColumnAllowed if false the function will not search and complete columns
   * @return -1 in case of no candidates found, 0 otherwise
   */
  public int completeName(String buffer, int cursor, List<CharSequence> candidates,
                          Map<String, String> aliases, boolean isColumnAllowed) {

    if (buffer == null) buffer = "";

    // no need to process after first point after cursor
    int nextPointPos = buffer.indexOf('.', cursor);
    if (nextPointPos != -1) buffer = buffer.substring(0, nextPointPos);

    // points divide the name to the schema, table and column - find them
    int pointPos1 = buffer.indexOf('.');
    int pointPos2 = buffer.indexOf('.', pointPos1 + 1);

    // find schema and table name if they are
    String schema;
    String table;
    String column;
    if (pointPos1 == -1) {             // process only schema or keyword case
      schema = buffer;
      int keywordsRes = completeKeyword(buffer, cursor, candidates);
      List<CharSequence> schemaCandidates = new ArrayList<>();
      int schemaRes = completeSchema(schema, cursor, schemaCandidates);
      candidates.addAll(schemaCandidates);
      return Math.max(keywordsRes, schemaRes);
    }
    else {
      schema = buffer.substring(0, pointPos1);
      if (aliases.containsKey(schema)) {  // process alias case
        String alias = aliases.get(schema);
        int pointPos = alias.indexOf('.');
        schema = alias.substring(0, pointPos);
        table = alias.substring(pointPos + 1);
        column = buffer.substring(pointPos1 + 1);
      }
      else if (pointPos2 == -1) {        // process schema.table case
        table = buffer.substring(pointPos1 + 1);
        return completeTable(schema, table, cursor - pointPos1 - 1, candidates);
      }
      else {
        table = buffer.substring(pointPos1 + 1, pointPos2);
        column = buffer.substring(pointPos2 + 1);
      }
    }

    // here in case of column
    if (isColumnAllowed)
      return completeColumn(schema, table, column, cursor - pointPos2 - 1, candidates);
    else
      return -1;
  }

  // test purpose only
  WhitespaceArgumentDelimiter getSqlDelimiter() {
    return this.sqlDelimiter;
  }
}
