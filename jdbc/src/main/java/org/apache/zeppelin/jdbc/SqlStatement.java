/*
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

import org.apache.commons.lang.StringUtils;
import jline.console.completer.ArgumentCompleter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

public class SqlStatement {
  private int cursorPosition;
  private String cursorString;
  private String schema;
  private String table;
  private String column;
  private HashMap<String, String> aliases = new HashMap<>();
  private HashSet<String> activeSchemaTables = new HashSet<>();

  private static final String KEYWORD_AS = "as";

  /**
   * Delimiter that can split SQL statement in keyword list.
   */
  private ArgumentCompleter.WhitespaceArgumentDelimiter sqlDelimiter = new ArgumentCompleter.
      WhitespaceArgumentDelimiter() {

    private Pattern pattern = Pattern.compile(",");

    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      return pattern.matcher("" + buffer.charAt(pos)).matches()
          || super.isDelimiterChar(buffer, pos);
    }
  };

  // test purpose only
  ArgumentCompleter.WhitespaceArgumentDelimiter getSqlDelimiter() {
    return this.sqlDelimiter;
  }

  SqlStatement(String statement, int cursor, String defaultSchema,
               Collection<String> schemas, Collection<String> tablesInDefaultSchema,
               Collection<String> keywords) {
    if (StringUtils.isNotBlank(statement) && statement.length() >= cursor) {
      ArgumentCompleter.ArgumentList argumentList = sqlDelimiter.delimit(statement, cursor);
      String cursorArgument = argumentList.getCursorArgument();
      int argumentPosition = argumentList.getArgumentPosition();

      setStatementArguments(argumentList.getArguments(), cursorArgument, defaultSchema, schemas,
          tablesInDefaultSchema, keywords);
      setCursorArgument(cursorArgument, argumentPosition, defaultSchema, schemas,
          tablesInDefaultSchema);
    }
  }

  private void setStatementArguments(String[] sqlArguments, String currentArgument,
                                     String defaultSchema, Collection<String> schemas,
                                     Collection<String> tablesInDefaultSchema,
                                     Collection<String> keywords) {
    String schemaTable = null;
    boolean isTable = false;

    for (int i = 0; i < sqlArguments.length; i++) {
      if (!currentArgument.equals(sqlArguments[i])) {
        int pointPos1 = sqlArguments[i].indexOf('.');
        if (pointPos1 > -1
            && sqlArguments[i].substring(pointPos1 + 1).indexOf('.') == -1
            && schemas.contains(sqlArguments[i].substring(0, pointPos1))) {
          schemaTable = sqlArguments[i];
          isTable = true;
        } else if (tablesInDefaultSchema.contains(sqlArguments[i])) {
          schemaTable = defaultSchema + "." + sqlArguments[i];
          isTable = true;
        }
        if (isTable) {
          isTable = false;
          this.activeSchemaTables.add(schemaTable);
          if (i + 2 < sqlArguments.length
              && sqlArguments[i + 1].toLowerCase().equals(KEYWORD_AS)) {
            this.aliases.put(sqlArguments[i + 2], schemaTable);
            i += 2;
          } else if (i + 1 < sqlArguments.length
              && sqlArguments[i + 1].matches("[a-zA-Z0-9]+")
              && !keywords.contains(sqlArguments[i + 1])) {
            this.aliases.put(sqlArguments[i + 1], schemaTable);
            i++;
          }
        }
      }
    }
  }

  private void setCursorArgument(String string, int cursor,
                                 String defaultSchema, Collection<String> schemas,
                                 Collection<String> tablesInDefaultSchema) {
    boolean defineColumns = false;

    if (cursor == 0) {
      this.cursorPosition = 0;
      return;
    }
    if (string != null) {
      int pointPos1 = string.indexOf('.');
      int pointPos2 = string.indexOf('.', pointPos1 + 1);
      if (pointPos1 > -1) {
        String string1 = string.substring(0, pointPos1).trim();
        if (schemas.contains(string1)) {
          this.schema = string1;
          if (pointPos2 > -1) {
            this.table = string.substring(pointPos1 + 1, pointPos2);
            this.column = string.substring(pointPos2 + 1);
            this.cursorPosition = cursor - pointPos2 - 1;
          } else {
            this.table = string.substring(pointPos1 + 1);
            this.cursorPosition = cursor - pointPos1 - 1;
          }
        } else {
          if (this.aliases.containsKey(string1)) {
            String schemaTable = this.aliases.get(string1);
            int pointSchemaTable = schemaTable.indexOf('.');
            this.schema = schemaTable.substring(0, pointSchemaTable);
            this.table = schemaTable.substring(pointSchemaTable + 1);
            defineColumns = true;
          } else if (tablesInDefaultSchema.contains(string1)) {
            this.schema = defaultSchema;
            this.table = string1;
            defineColumns = true;
          }
          if (defineColumns) {
            this.column = string.substring(pointPos1 + 1);
            this.cursorPosition = cursor - pointPos1 - 1;
          }
        }
      } else {
        this.cursorString = string;
        this.cursorPosition = cursor;
      }
    }
  }

  String getCursorString() {
    return cursorString;
  }

  String getSchema() {
    return schema;
  }

  String getTable() {
    return table;
  }

  String getColumn() {
    return column;
  }

  int getCursorPosition() {
    return cursorPosition;
  }

  boolean needLoadTables() {
    return (schema != null) && (table != null) && (column == null);
  }

  Collection<String> getActiveSchemaTables() {
    if ((schema != null) && (table != null) && (column != null)) {
      activeSchemaTables.add(schema + "." + table);
    }
    return Collections.unmodifiableSet(activeSchemaTables);
  }
}
