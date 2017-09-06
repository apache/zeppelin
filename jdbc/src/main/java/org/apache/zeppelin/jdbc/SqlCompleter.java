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
import org.apache.zeppelin.completer.CachedCompleter;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.completer.StringsCompleter;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;

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
  private CachedCompleter schemasCompleter;

  /**
   * Contain different completer with table list for every schema name
   */
  private Map<String, CachedCompleter> tablesCompleters = new HashMap<>();

  /**
   * Contains different completer with column list for every table name
   * Table names store as schema_name.table_name
   */
  private Map<String, CachedCompleter> columnsCompleters = new HashMap<>();

  /**
   * Completer for sql keywords
   */
  private CachedCompleter keywordCompleter;

  private int ttlInSeconds;


  public SqlCompleter(int ttlInSeconds) {
    this.ttlInSeconds = ttlInSeconds;
  }

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

    int complete = completeName(cursorArgument, argumentPosition, candidates,
            findAliasesInSQL(argumentList.getArguments()));

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


  private static void fillTableNames(String schema, DatabaseMetaData meta, Set<String> tables) {
    try (ResultSet tbls = meta.getTables(schema, schema, "%",
        new String[]{"TABLE", "VIEW", "ALIAS", "SYNONYM", "GLOBAL TEMPORARY", "LOCAL TEMPORARY"})) {
      while (tbls.next()) {
        String table = tbls.getString("TABLE_NAME");
        tables.add(table);
      }
    } catch (Throwable t) {
      logger.error("Failed to retrieve the table name", t);
    }
  }

  /**
   * Fill two map with list of tables and list of columns
   *
   * @param schema name of a scheme
   * @param table name of a table
   * @param meta meta metadata from connection to database
   * @param columns function fills this set, for every table name adds set
   *        of columns within the table; table name is in format schema_name.table_name
   */
  private static void fillColumnNames(String schema, String table, DatabaseMetaData meta,
      Set<String> columns) {
    try (ResultSet cols = meta.getColumns(schema, schema, table, "%")) {
      while (cols.next()) {
        String column = cols.getString("COLUMN_NAME");
        columns.add(column);
      }
    } catch (Throwable t) {
      logger.error("Failed to retrieve the column name", t);
    }
  }

  public static Set<String> getSqlKeywordsCompletions(DatabaseMetaData meta) throws IOException,
          SQLException {

    // Add the default SQL completions
    String keywords =
            new BufferedReader(new InputStreamReader(
                    SqlCompleter.class.getResourceAsStream("/ansi.sql.keywords"))).readLine();

    Set<String> completions = new TreeSet<>();

    if (null != meta) {

      // Add the driver specific SQL completions
      String driverSpecificKeywords =
              "/" + meta.getDriverName().replace(" ", "-").toLowerCase() + "-sql.keywords";
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
        keywords += "," + meta.getSQLKeywords();
      } catch (Exception e) {
        logger.debug("fail to get SQL key words from database metadata: " + e, e);
      }
      try {
        keywords += "," + meta.getStringFunctions();
      } catch (Exception e) {
        logger.debug("fail to get string function names from database metadata: " + e, e);
      }
      try {
        keywords += "," + meta.getNumericFunctions();
      } catch (Exception e) {
        logger.debug("fail to get numeric function names from database metadata: " + e, e);
      }
      try {
        keywords += "," + meta.getSystemFunctions();
      } catch (Exception e) {
        logger.debug("fail to get system function names from database metadata: " + e, e);
      }
      try {
        keywords += "," + meta.getTimeDateFunctions();
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
   * Initializes all local completers from database connection
   *
   * @param connection database connection
   * @param schemaFiltersString a comma separated schema name patterns, supports '%'  symbol;
   *        for example "prod_v_%,prod_t_%"
   */
  public void createOrUpdateFromConnection(Connection connection, String schemaFiltersString,
      String buffer, int cursor) {
    try (Connection c = connection) {
      if (schemaFiltersString == null) {
        schemaFiltersString = StringUtils.EMPTY;
      }
      List<String> schemaFilters = Arrays.asList(schemaFiltersString.split(","));
      CursorArgument cursorArgument = parseCursorArgument(buffer, cursor);

      Set<String> tables = new HashSet<>();
      Set<String> columns = new HashSet<>();
      Set<String> schemas = new HashSet<>();
      Set<String> catalogs = new HashSet<>();
      Set<String> keywords = new HashSet<>();

      if (c != null) {
        DatabaseMetaData databaseMetaData = c.getMetaData();
        if (keywordCompleter == null || keywordCompleter.getCompleter() == null
            || keywordCompleter.isExpired()) {
          keywords = getSqlKeywordsCompletions(databaseMetaData);
          initKeywords(keywords);
        }
        if (cursorArgument.needLoadSchemas() &&
            (schemasCompleter == null || schemasCompleter.getCompleter() == null
            || schemasCompleter.isExpired())) {
          schemas = getSchemaNames(databaseMetaData, schemaFilters);
          catalogs = getCatalogNames(databaseMetaData, schemaFilters);

          if (schemas.size() == 0) {
            schemas.addAll(catalogs);
          }

          initSchemas(schemas);
        }

        CachedCompleter tablesCompleter = tablesCompleters.get(cursorArgument.getSchema());
        if (cursorArgument.needLoadTables() &&
            (tablesCompleter == null || tablesCompleter.isExpired())) {
          fillTableNames(cursorArgument.getSchema(), databaseMetaData, tables);
          initTables(cursorArgument.getSchema(), tables);
        }

        String schemaTable =
            String.format("%s.%s", cursorArgument.getSchema(), cursorArgument.getTable());
        CachedCompleter columnsCompleter = columnsCompleters.get(schemaTable);

        if (cursorArgument.needLoadColumns() &&
            (columnsCompleter == null || columnsCompleter.isExpired())) {
          fillColumnNames(cursorArgument.getSchema(), cursorArgument.getTable(), databaseMetaData,
              columns);
          initColumns(schemaTable, columns);
        }

        logger.info("Completer initialized with " + schemas.size() + " schemas, " +
            columns.size() + " tables and " + keywords.size() + " keywords");
      }

    } catch (SQLException | IOException e) {
      logger.error("Failed to update the metadata completions", e);
    }
  }



  public void initKeywords(Set<String> keywords) {
    if (keywords != null && !keywords.isEmpty()) {
      keywordCompleter = new CachedCompleter(new StringsCompleter(keywords), 0);
    }
  }

  public void initSchemas(Set<String> schemas) {
    if (schemas != null && !schemas.isEmpty()) {
      schemasCompleter = new CachedCompleter(
          new StringsCompleter(new TreeSet<>(schemas)), ttlInSeconds);
    }
  }

  public void initTables(String schema, Set<String> tables) {
    if (tables != null && !tables.isEmpty()) {
      tablesCompleters.put(schema, new CachedCompleter(
          new StringsCompleter(new TreeSet<>(tables)), ttlInSeconds));
    }
  }

  public void initColumns(String schemaTable, Set<String> columns) {
    if (columns != null && !columns.isEmpty()) {
      columnsCompleters.put(schemaTable,
          new CachedCompleter(new StringsCompleter(columns), ttlInSeconds));
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
    return keywordCompleter.getCompleter().complete(buffer, cursor, candidates);
  }

  /**
   * Complete buffer in case it is a schema name
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeSchema(String buffer, int cursor, List<CharSequence> candidates) {
    return schemasCompleter.getCompleter().complete(buffer, cursor, candidates);
  }

  /**
   * Complete buffer in case it is a table name
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeTable(String schema, String buffer, int cursor,
                            List<CharSequence> candidates) {
    // Wrong schema
    if (schema == null || !tablesCompleters.containsKey(schema))
      return -1;
    else {
      return tablesCompleters.get(schema).getCompleter().complete(buffer, cursor, candidates);
    }
  }

  /**
   * Complete buffer in case it is a column name
   *
   * @return -1 in case of no candidates found, 0 otherwise
   */
  private int completeColumn(String schema, String table, String buffer, int cursor,
                             List<CharSequence> candidates) {
    // Wrong schema or wrong table
    if (schema == null || table == null || !columnsCompleters.containsKey(schema + "." + table)) {
      return -1;
    } else {
      return columnsCompleters.get(schema + "." + table).getCompleter()
          .complete(buffer, cursor, candidates);
    }
  }

  /**
   * Complete buffer with a single name. Function will decide what it is:
   * a schema, a table of a column or a keyword
   *
   * @param aliases for every alias contains table name in format schema_name.table_name
   * @return -1 in case of no candidates found, 0 otherwise
   */
  public int completeName(String buffer, int cursor, List<InterpreterCompletion> candidates,
                          Map<String, String> aliases) {
    CursorArgument cursorArgument = parseCursorArgument(buffer, cursor);

    // find schema and table name if they are
    String schema;
    String table;
    String column;

    if (cursorArgument.getSchema() == null) {             // process all
      List<CharSequence> keywordsCandidates = new ArrayList();
      List<CharSequence> schemaCandidates = new ArrayList<>();
      int keywordsRes = completeKeyword(buffer, cursor, keywordsCandidates);
      int schemaRes = completeSchema(buffer, cursor, schemaCandidates);
      addCompletions(candidates, keywordsCandidates, CompletionType.keyword.name());
      addCompletions(candidates, schemaCandidates, CompletionType.schema.name());
      return NumberUtils.max(new int[]{keywordsRes, schemaRes});
    } else {
      schema = cursorArgument.getSchema();
      if (aliases.containsKey(schema)) {  // process alias case
        String alias = aliases.get(schema);
        int pointPos = alias.indexOf('.');
        schema = alias.substring(0, pointPos);
        table = alias.substring(pointPos + 1);
        column = cursorArgument.getColumn();
        List<CharSequence> columnCandidates = new ArrayList();
        int columnRes = completeColumn(schema, table, column, cursorArgument.getCursorPosition(),
            columnCandidates);
        addCompletions(candidates, columnCandidates, CompletionType.column.name());
        // process schema.table case
      } else if (cursorArgument.getTable() != null && cursorArgument.getColumn() == null) {
        List<CharSequence> tableCandidates = new ArrayList();
        table = cursorArgument.getTable();
        int tableRes = completeTable(schema, table, cursorArgument.getCursorPosition(),
            tableCandidates);
        addCompletions(candidates, tableCandidates, CompletionType.table.name());
        return tableRes;
      } else {
        List<CharSequence> columnCandidates = new ArrayList();
        table = cursorArgument.getTable();
        column = cursorArgument.getColumn();
        int columnRes = completeColumn(schema, table, column, cursorArgument.getCursorPosition(),
            columnCandidates);
        addCompletions(candidates, columnCandidates, CompletionType.column.name());
      }
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

  private CursorArgument parseCursorArgument(String buffer, int cursor) {
    CursorArgument result = new CursorArgument();
    if (buffer != null && buffer.length() >= cursor) {
      String buf = buffer.substring(0, cursor);
      if (StringUtils.isNotBlank(buf)) {
        ArgumentList argumentList = sqlDelimiter.delimit(buf, cursor);
        String cursorArgument = argumentList.getCursorArgument();
        if (cursorArgument != null) {
          int pointPos1 = cursorArgument.indexOf('.');
          int pointPos2 = cursorArgument.indexOf('.', pointPos1 + 1);
          if (pointPos1 > -1) {
            result.setSchema(cursorArgument.substring(0, pointPos1).trim());
            if (pointPos2 > -1) {
              result.setTable(cursorArgument.substring(pointPos1 + 1, pointPos2));
              result.setColumn(cursorArgument.substring(pointPos2 + 1));
              result.setCursorPosition(cursor - pointPos2 - 1);
            } else {
              result.setTable(cursorArgument.substring(pointPos1 + 1));
              result.setCursorPosition(cursor - pointPos1 - 1);
            }
          }
        }
      }
    }

    return result;
  }

  private class CursorArgument {
    private String schema;
    private String table;
    private String column;
    private int cursorPosition;

    public String getSchema() {
      return schema;
    }

    public void setSchema(String schema) {
      this.schema = schema;
    }

    public String getTable() {
      return table;
    }

    public void setTable(String table) {
      this.table = table;
    }

    public String getColumn() {
      return column;
    }

    public void setColumn(String column) {
      this.column = column;
    }

    public int getCursorPosition() {
      return cursorPosition;
    }

    public void setCursorPosition(int cursorPosition) {
      this.cursorPosition = cursorPosition;
    }

    public boolean needLoadSchemas() {
      if (table == null && column == null) {
        return true;
      }
      return false;
    }

    public boolean needLoadTables() {
      if (schema != null && table != null && column == null) {
        return true;
      }
      return false;
    }

    public boolean needLoadColumns() {
      if (schema != null && table != null && column != null) {
        return true;
      }
      return false;
    }
  }
}
