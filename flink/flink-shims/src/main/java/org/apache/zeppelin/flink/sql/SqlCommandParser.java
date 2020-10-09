/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink.sql;

import org.apache.zeppelin.flink.FlinkShims;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Simple parser for determining the type of command and its parameters.
 * All the SqlCommands should be put into this class, and the parsing logic needs to be put ito FlinkShims
 * because each version of flink has different sql syntax support.
 */
public final class SqlCommandParser {

  private FlinkShims flinkShims;
  private Object tableEnv;

  public SqlCommandParser(FlinkShims flinkShims, Object tableEnv) {
    this.flinkShims = flinkShims;
    this.tableEnv = tableEnv;
  }

  public Optional<SqlCommandCall> parse(String stmt) {
    return flinkShims.parseSql(tableEnv, stmt);
  }

  // --------------------------------------------------------------------------------------------

  private static final Function<String[], Optional<String[]>> NO_OPERANDS =
          (operands) -> Optional.of(new String[0]);

  private static final Function<String[], Optional<String[]>> SINGLE_OPERAND =
          (operands) -> Optional.of(new String[]{operands[0]});

  private static final int DEFAULT_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

  /**
   * Supported SQL commands.
   */
  public enum SqlCommand {
    QUIT(
            "(QUIT|EXIT)",
            NO_OPERANDS),

    CLEAR(
            "CLEAR",
            NO_OPERANDS),

    HELP(
            "HELP",
            NO_OPERANDS),

    SHOW_CATALOGS(
            "SHOW\\s+CATALOGS",
            NO_OPERANDS),

    SHOW_DATABASES(
            "SHOW\\s+DATABASES",
            NO_OPERANDS),

    SHOW_TABLES(
            "SHOW\\s+TABLES",
            NO_OPERANDS),

    SHOW_FUNCTIONS(
            "SHOW\\s+FUNCTIONS",
            NO_OPERANDS),

    SHOW_MODULES(
            "SHOW\\s+MODULES",
            NO_OPERANDS),

    USE_CATALOG(
            "USE\\s+CATALOG\\s+(.*)",
            SINGLE_OPERAND),

    USE(
            "USE\\s+(?!CATALOG)(.*)",
            SINGLE_OPERAND),

    CREATE_CATALOG(null, SINGLE_OPERAND),

    DROP_CATALOG(null, SINGLE_OPERAND),

    DESC(
            "DESC\\s+(.*)",
            SINGLE_OPERAND),

    DESCRIBE(
            "DESCRIBE\\s+(.*)",
            SINGLE_OPERAND),

    EXPLAIN(
            "EXPLAIN\\s+(SELECT|INSERT)\\s+(.*)",
            (operands) -> {
              return Optional.of(new String[] { operands[0], operands[1] });
            }),

    CREATE_DATABASE(
            "(CREATE\\s+DATABASE\\s+.*)",
            SINGLE_OPERAND),

    DROP_DATABASE(
            "(DROP\\s+DATABASE\\s+.*)",
            SINGLE_OPERAND),

    ALTER_DATABASE(
            "(ALTER\\s+DATABASE\\s+.*)",
            SINGLE_OPERAND),

    CREATE_TABLE("(CREATE\\s+TABLE\\s+.*)", SINGLE_OPERAND),

    DROP_TABLE("(DROP\\s+TABLE\\s+.*)", SINGLE_OPERAND),

    ALTER_TABLE(
            "(ALTER\\s+TABLE\\s+.*)",
            SINGLE_OPERAND),

    DROP_VIEW(
            "DROP\\s+VIEW\\s+(.*)",
            SINGLE_OPERAND),

    CREATE_VIEW(
            "CREATE\\s+VIEW\\s+(\\S+)\\s+AS\\s+(.*)",
            (operands) -> {
              if (operands.length < 2) {
                return Optional.empty();
              }
              return Optional.of(new String[]{operands[0], operands[1]});
            }),

    CREATE_FUNCTION(null, SINGLE_OPERAND),

    DROP_FUNCTION(null, SINGLE_OPERAND),

    ALTER_FUNCTION(null, SINGLE_OPERAND),

    SELECT(
            "(SELECT.*)",
            SINGLE_OPERAND),

    INSERT_INTO(
            "(INSERT\\s+INTO.*)",
            SINGLE_OPERAND),

    INSERT_OVERWRITE(
            "(INSERT\\s+OVERWRITE.*)",
            SINGLE_OPERAND),

    SET(
            "SET(\\s+(\\S+)\\s*=(.*))?", // whitespace is only ignored on the left side of '='
            (operands) -> {
              if (operands.length < 3) {
                return Optional.empty();
              } else if (operands[0] == null) {
                return Optional.of(new String[0]);
              }
              return Optional.of(new String[]{operands[1], operands[2]});
            }),

    RESET(
            "RESET",
            NO_OPERANDS),

    SOURCE(
            "SOURCE\\s+(.*)",
            SINGLE_OPERAND);

    public final Pattern pattern;
    public final Function<String[], Optional<String[]>> operandConverter;

    SqlCommand(String matchingRegex, Function<String[], Optional<String[]>> operandConverter) {
      if (matchingRegex == null) {
        this.pattern = null;
      } else {
        this.pattern = Pattern.compile(matchingRegex, DEFAULT_PATTERN_FLAGS);
      }
      this.operandConverter = operandConverter;
    }

    @Override
    public String toString() {
      return super.toString().replace('_', ' ');
    }

    public boolean hasOperands() {
      return operandConverter != NO_OPERANDS;
    }
  }

  /**
   * Call of SQL command with operands and command type.
   */
  public static class SqlCommandCall {
    public final SqlCommand command;
    public final String[] operands;
    public final String sql;

    public SqlCommandCall(SqlCommand command, String[] operands, String sql) {
      this.command = command;
      this.operands = operands;
      this.sql = sql;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SqlCommandCall that = (SqlCommandCall) o;
      return command == that.command && Arrays.equals(operands, that.operands);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(command);
      result = 31 * result + Arrays.hashCode(operands);
      return result;
    }

    @Override
    public String toString() {
      return command + "(" + Arrays.toString(operands) + ")";
    }
  }
}
