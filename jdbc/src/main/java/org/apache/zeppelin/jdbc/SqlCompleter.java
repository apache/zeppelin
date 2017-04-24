package org.apache.zeppelin.jdbc;

/*
 * This source file is based on code taken from SQLLine 1.0.2 See SQLLine notice in LICENSE
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.completer.StringsCompleter;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;

import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * SQL auto complete functionality for the JdbcInterpreter.
 */
public class SqlCompleter {

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

  public int complete(String buffer, int cursor, List<InterpreterCompletion> candidates) {

    logger.debug("Complete with buffer = " + buffer + ", cursor = " + cursor);

    // The delimiter breaks the buffer into separate words (arguments), separated by the
    // white spaces.
    ArgumentList argumentList = sqlDelimiter.delimit(buffer, cursor);

    Pattern whitespaceEndPatter = Pattern.compile("\\s$");
    String cursorArgument = null;
    int argumentPosition;
    if (buffer.length() == 0 || whitespaceEndPatter.matcher(buffer).find()) {
      argumentPosition = buffer.length() - 1;
    } else {
      cursorArgument = argumentList.getCursorArgument();
      argumentPosition = argumentList.getArgumentPosition();
    }

    boolean isColumnAllowed = true;
    if (buffer.length() > 0) {
      String beforeCursorBuffer = buffer.substring(0,
          Math.min(cursor, buffer.length())).toUpperCase();
      // check what sql is and where cursor is to allow column completion or not
      if (beforeCursorBuffer.contains("SELECT ") && beforeCursorBuffer.contains(" FROM ")
          && !beforeCursorBuffer.contains(" WHERE "))
        isColumnAllowed = false;
    }

    int complete = completeName(cursorArgument, argumentPosition, candidates,
            findAliasesInSQL(argumentList.getArguments()), isColumnAllowed);

    if (candidates.size() == 1) {
      InterpreterCompletion interpreterCompletion = candidates.get(0);
      interpreterCompletion.setName(interpreterCompletion.getName() + " ");
      interpreterCompletion.setValue(interpreterCompletion.getValue() + " ");
      candidates.set(0, interpreterCompletion);
    }
    logger.debug("complete:" + complete + ", size:" + candidates.size());
    return complete;
  }

  /**
   * Return list of schema names within the database
   *
   * @param meta metadata from connection to database
   * @param schemaFilters a schema name patterns; must match the schema name
   *        as it is stored in the database; "" retrieves those without a schema;
   *        <code>null</code> means that the schema name should not be used to narrow
   *        the search; supports '%'; for example "prod_v_%"
   * @return set of all schema names in the database
   */
  private static Set<String> getSchemaNames(DatabaseMetaData meta, List<String> schemaFilters) {
    Set<String> res = new HashSet<>();
    try {
      ResultSet schemas = meta.getSchemas();
      try {
        while (schemas.next()) {
          String schemaName = schemas.getString("TABLE_SCHEM");
          if (schemaName == null) {
            schemaName = "";
          }
          for (String schemaFilter : schemaFilters) {
            if (schemaFilter.equals("") || schemaName.matches(schemaFilter.replace("%", ".*?"))) {
              res.add(schemaName);
            }
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
   * Return list of catalog names within the database
   *
   * @param meta metadata from connection to database
   * @param schemaFilters a catalog name patterns; must match the catalog name
   *        as it is stored in the database; "" retrieves those without a catalog;
   *        <code>null</code> means that the schema name should not be used to narrow
   *        the search; supports '%'; for example "prod_v_%"
   * @return set of all catalog names in the database
   */
  private static Set<String> getCatalogNames(DatabaseMetaData meta, List<String> schemaFilters) {
    Set<String> res = new HashSet<>();
    try {
      ResultSet schemas = meta.getCatalogs();
      try {
        while (schemas.next()) {
          String schemaName = schemas.getString("TABLE_CAT");
          for (String schemaFilter : schemaFilters) {
            if (schemaFilter.equals("") || schemaName.matches(schemaFilter.replace("%", ".*?"))) {
              res.add(schemaName);
            }
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
   * @param catalogName name of a catalog
   * @param meta metadata from connection to database
   * @param schemaFilter a schema name pattern; must match the schema name
   *        as it is stored in the database; "" retrieves those without a schema;
   *        <code>null</code> means that the schema name should not be used to narrow
   *        the search; supports '%'; for example "prod_v_%"
   * @param tables function fills this map, for every schema name adds
   *        set of table names within the schema
   * @param columns function fills this map, for every table name adds set
   *        of columns within the table; table name is in format schema_name.table_name
   */
  private static void fillTableAndColumnNames(String catalogName, DatabaseMetaData meta,
                                              String schemaFilter,
                                              Map<String, Set<String>> tables,
                                              Map<String, Set<String>> columns)  {
    try {
      ResultSet cols = meta.getColumns(catalogName, StringUtils.EMPTY, "%", "%");
      try {
        while (cols.next()) {
          String schema = cols.getString("TABLE_SCHEM");
          if (schema == null) {
            schema = cols.getString("TABLE_CAT");
          }
          if (!schemaFilter.equals("") && !schema.matches(schemaFilter.replace("%", ".*?"))) {
            continue;
          }
          String table = cols.getString("TABLE_NAME");
          String column = cols.getString("COLUMN_NAME");
          if (!isBlank(table)) {
            String schemaTable = schema + "." + table;
            if (!columns.containsKey(schemaTable)) {
              columns.put(schemaTable, new HashSet<String>());
            }
            columns.get(schemaTable).add(column);
            if (!tables.containsKey(schema)) {
              tables.put(schema, new HashSet<String>());
            }
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
                          SqlCompleter.class.getResourceAsStream(driverSpecificKeywords)))
                          .readLine();
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
   * @param schemaFiltersString a comma separated schema name patterns; supports '%'  symbol;
   * for example "prod_v_%,prod_t_%"
   */
  public void initFromConnection(Connection connection, String schemaFiltersString) {
    if (schemaFiltersString == null) {
      schemaFiltersString = StringUtils.EMPTY;
    }
    List<String> schemaFilters = Arrays.asList(schemaFiltersString.split(","));

    try (Connection c = connection) {
      Map<String, Set<String>> tables = new HashMap<>();
      Map<String, Set<String>> columns = new HashMap<>();
      Set<String> schemas = new HashSet<>();
      Set<String> catalogs = new HashSet<>();
      Set<String> keywords = getSqlKeywordsCompletions(connection);
      if (connection != null) {
        schemas = getSchemaNames(connection.getMetaData(), schemaFilters);
        catalogs = getCatalogNames(connection.getMetaData(), schemaFilters);
        if (schemas.size() == 0) {
          schemas.addAll(catalogs);
        }
        for (String schema : schemas) {
          for (String schemaFilter : schemaFilters) {
            fillTableAndColumnNames(schema, connection.getMetaData(), schemaFilter, tables,
                columns);
          }
        }
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
    if (schema == null) {
      int res = -1;
      Set<CharSequence> candidatesSet = new HashSet<>();
      for (StringsCompleter stringsCompleter : tablesCompleters.values()) {
        int resTable = stringsCompleter.complete(buffer, cursor, candidatesSet);
        res = Math.max(res, resTable);
      }
      candidates.addAll(candidatesSet);
      return res;
    }
    // Wrong schema
    if (!tablesCompleters.containsKey(schema) && schema != null)
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
    if (table == null && schema == null) {
      int res = -1;
      Set<CharSequence> candidatesSet = new HashSet<>();
      for (StringsCompleter stringsCompleter : columnsCompleters.values()) {
        int resColumn = stringsCompleter.complete(buffer, cursor, candidatesSet);
        res = Math.max(res, resColumn);
      }
      candidates.addAll(candidatesSet);
      return res;
    }
    // Wrong schema or wrong table
    if (!tablesCompleters.containsKey(schema) ||
        !columnsCompleters.containsKey(schema + "." + table)) {
      return -1;
    } else {
      return columnsCompleters.get(schema + "." + table).complete(buffer, cursor, candidates);
    }
  }

  /**
   * Complete buffer with a single name. Function will decide what it is:
   * a schema, a table of a column or a keyword
   *
   * @param aliases for every alias contains table name in format schema_name.table_name
   * @param isColumnAllowed if false the function will not search and complete columns
   * @return -1 in case of no candidates found, 0 otherwise
   */
  public int completeName(String buffer, int cursor, List<InterpreterCompletion> candidates,
                          Map<String, String> aliases, boolean isColumnAllowed) {

    // points divide the name to the schema, table and column - find them
    int pointPos1 = -1;
    int pointPos2 = -1;

    if (StringUtils.isNotEmpty(buffer)) {
      if (buffer.length() > cursor) {
        buffer = buffer.substring(0, cursor + 1);
      }
      pointPos1 = buffer.indexOf('.');
      pointPos2 = buffer.indexOf('.', pointPos1 + 1);
    }
    // find schema and table name if they are
    String schema;
    String table;
    String column;

    if (pointPos1 == -1) {             // process all
      List<CharSequence> keywordsCandidates = new ArrayList();
      List<CharSequence> schemaCandidates = new ArrayList<>();
      List<CharSequence> tableCandidates = new ArrayList<>();
      List<CharSequence> columnCandidates = new ArrayList<>();
      int keywordsRes = completeKeyword(buffer, cursor, keywordsCandidates);
      int schemaRes = completeSchema(buffer, cursor, schemaCandidates);
      int tableRes = completeTable(null, buffer, cursor, tableCandidates);
      int columnRes = -1;
      if (isColumnAllowed) {
        columnRes = completeColumn(null, null, buffer, cursor, columnCandidates);
      }
      addCompletions(candidates, keywordsCandidates, CompletionType.keyword.name());
      addCompletions(candidates, schemaCandidates, CompletionType.schema.name());
      addCompletions(candidates, tableCandidates, CompletionType.table.name());
      addCompletions(candidates, columnCandidates, CompletionType.column.name());
      return NumberUtils.max(new int[]{keywordsRes, schemaRes, tableRes, columnRes});
    } else {
      schema = buffer.substring(0, pointPos1);
      if (aliases.containsKey(schema)) {  // process alias case
        String alias = aliases.get(schema);
        int pointPos = alias.indexOf('.');
        schema = alias.substring(0, pointPos);
        table = alias.substring(pointPos + 1);
        column = buffer.substring(pointPos1 + 1);
      } else if (pointPos2 == -1) {        // process schema.table case
        List<CharSequence> tableCandidates = new ArrayList();
        table = buffer.substring(pointPos1 + 1);
        int tableRes = completeTable(schema, table, cursor - pointPos1 - 1, tableCandidates);
        addCompletions(candidates, tableCandidates, CompletionType.table.name());
        return tableRes;
      } else {
        table = buffer.substring(pointPos1 + 1, pointPos2);
        column = buffer.substring(pointPos2 + 1);
      }
    }

    // here in case of column
    if (table != null && isColumnAllowed) {
      List<CharSequence> columnCandidates = new ArrayList();
      int columnRes = completeColumn(schema, table, column, cursor - pointPos2 - 1,
          columnCandidates);
      addCompletions(candidates, columnCandidates, CompletionType.column.name());
      return columnRes;
    }

    return -1;
  }

  // test purpose only
  WhitespaceArgumentDelimiter getSqlDelimiter() {
    return this.sqlDelimiter;
  }

  private void addCompletions(List<InterpreterCompletion> interpreterCompletions,
      List<CharSequence> candidates, String meta) {
    for (CharSequence candidate : candidates) {
      interpreterCompletions.add(new InterpreterCompletion(candidate.toString(),
          candidate.toString(), meta));
    }
  }
}
