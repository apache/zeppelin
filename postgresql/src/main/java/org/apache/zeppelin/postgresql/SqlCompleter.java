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
package org.apache.zeppelin.postgresql;

/*
 * This source file is based on code taken from SQLLine 1.0.2 See SQLLine notice in LICENSE
 */
import static org.apache.commons.lang.StringUtils.isBlank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;
import jline.console.completer.StringsCompleter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * SQL auto complete functionality for the PostgreSqlInterpreter.
 */
public class SqlCompleter extends StringsCompleter {

  private static Logger logger = LoggerFactory.getLogger(SqlCompleter.class);

  /**
   * Delimiter that can split SQL statement in keyword list
   */
  private WhitespaceArgumentDelimiter sqlDelimiter = new WhitespaceArgumentDelimiter() {

    private Pattern pattern = Pattern.compile("[\\.:;,]");

    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      return pattern.matcher("" + buffer.charAt(pos)).matches()
          || super.isDelimiterChar(buffer, pos);
    }
  };

  private Set<String> modelCompletions = new HashSet<>();

  public SqlCompleter(Set<String> allCompletions, Set<String> dataModelCompletions) {
    super(allCompletions);
    this.modelCompletions = dataModelCompletions;
  }

  @Override
  public int complete(String buffer, int cursor, List<CharSequence> candidates) {

    if (isBlank(buffer) || (cursor > buffer.length() + 1)) {
      return -1;
    }

    // The delimiter breaks the buffer into separate words (arguments), separated by the
    // white spaces.
    ArgumentList argumentList = sqlDelimiter.delimit(buffer, cursor);
    String argument = argumentList.getCursorArgument();
    // cursor in the selected argument
    int argumentPosition = argumentList.getArgumentPosition();

    if (isBlank(argument)) {
      int argumentsCount = argumentList.getArguments().length;
      if (argumentsCount <= 0 || ((buffer.length() + 2) < cursor)
          || sqlDelimiter.isDelimiterChar(buffer, cursor - 2)) {
        return -1;
      }
      argument = argumentList.getArguments()[argumentsCount - 1];
      argumentPosition = argument.length();
    }

    int complete = super.complete(argument, argumentPosition, candidates);

    logger.debug("complete:" + complete + ", size:" + candidates.size());

    return complete;
  }

  public void updateDataModelMetaData(Connection connection) {

    try {
      Set<String> newModelCompletions = getDataModelMetadataCompletions(connection);
      logger.debug("New model metadata is:" + Joiner.on(',').join(newModelCompletions));

      // Sets.difference(set1, set2) - returned set contains all elements that are contained by set1
      // and not contained by set2. set2 may also contain elements not present in set1; these are
      // simply ignored.
      SetView<String> removedCompletions = Sets.difference(modelCompletions, newModelCompletions);
      logger.debug("Removed Model Completions: " + Joiner.on(',').join(removedCompletions));
      this.getStrings().removeAll(removedCompletions);

      SetView<String> newCompletions = Sets.difference(newModelCompletions, modelCompletions);
      logger.debug("New Completions: " + Joiner.on(',').join(newCompletions));
      this.getStrings().addAll(newCompletions);

      modelCompletions = newModelCompletions;

    } catch (SQLException e) {
      logger.error("Failed to update the metadata conmpletions", e);
    }
  }

  public static Set<String> getSqlKeywordsCompletions(Connection connection) throws IOException,
      SQLException {

    // Add the default SQL completions
    String keywords =
        new BufferedReader(new InputStreamReader(
            SqlCompleter.class.getResourceAsStream("/ansi.sql.keywords"))).readLine();

    DatabaseMetaData metaData = connection.getMetaData();

    // Add the driver specific SQL completions
    String driverSpecificKeywords =
        "/" + metaData.getDriverName().replace(" ", "-").toLowerCase() + "-sql.keywords";

    logger.info("JDBC DriverName:" + driverSpecificKeywords);

    if (SqlCompleter.class.getResource(driverSpecificKeywords) != null) {
      String driverKeywords =
          new BufferedReader(new InputStreamReader(
              SqlCompleter.class.getResourceAsStream(driverSpecificKeywords))).readLine();
      keywords += "," + driverKeywords.toUpperCase();
    }

    Set<String> completions = new TreeSet<>();


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

    // Also allow lower-case versions of all the keywords
    keywords += "," + keywords.toLowerCase();

    StringTokenizer tok = new StringTokenizer(keywords, ", ");
    while (tok.hasMoreTokens()) {
      completions.add(tok.nextToken());
    }

    return completions;
  }

  public static Set<String> getDataModelMetadataCompletions(Connection connection)
      throws SQLException {
    Set<String> completions = new TreeSet<>();
    getColumnNames(connection.getMetaData(), completions);
    getSchemaNames(connection.getMetaData(), completions);
    return completions;
  }

  private static void getColumnNames(DatabaseMetaData meta, Set<String> names) throws SQLException {

    try {
      ResultSet columns = meta.getColumns(meta.getConnection().getCatalog(), null, "%", "%");
      try {

        while (columns.next()) {
          // Add the following strings: (1) column name, (2) table name
          String name = columns.getString("TABLE_NAME");
          if (!isBlank(name)) {
            names.add(name);
            names.add(columns.getString("COLUMN_NAME"));
            // names.add(columns.getString("TABLE_NAME") + "." + columns.getString("COLUMN_NAME"));
          }
        }
      } finally {
        columns.close();
      }

      logger.debug(Joiner.on(',').join(names));
    } catch (Throwable t) {
      logger.error("Failed to retrieve the column name", t);
    }
  }

  private static void getSchemaNames(DatabaseMetaData meta, Set<String> names) throws SQLException {

    try {
      ResultSet schemas = meta.getSchemas();
      try {
        while (schemas.next()) {
          String schemaName = schemas.getString("TABLE_SCHEM");
          if (!isBlank(schemaName)) {
            names.add(schemaName + ".");
          }
        }
      } finally {
        schemas.close();
      }
    } catch (Throwable t) {
      logger.error("Failed to retrieve the column name", t);
    }
  }

  // test purpose only
  WhitespaceArgumentDelimiter getSqlDelimiter() {
    return this.sqlDelimiter;
  }
}
