/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.tajo.thrift;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.tajo.conf.TajoConf;
import org.apache.tajo.thrift.ThriftServerConstants;
import org.apache.tajo.thrift.client.TajoThriftClient;
import org.apache.tajo.thrift.generated.TBriefQueryInfo;
import org.apache.tajo.thrift.generated.TColumn;
import org.apache.tajo.thrift.generated.TGetQueryStatusResponse;
import org.apache.tajo.thrift.generated.TSchema;
import org.apache.tajo.thrift.generated.TTableDesc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ServiceException;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;

/**
 * Tajo interpreter implementation. with thrift protocol support
 * https://github.com/jerryjung/tajo/tree/thriftserver
 */
public class TajoInterpreter extends Interpreter {
  static {
    Interpreter.register(
        "tsqlt",
        "tajo-thrift",
        TajoInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
          .add("tajo.thrift.server",
               "localhost:" + ThriftServerConstants.DEFAULT_LISTEN_PORT,
               "Tajo thrift server address, host:port")
          .add("tajo.maxResult",
              "1000",
               "Maximum number of result to retreive")
          .build());
  }

  Logger logger = LoggerFactory.getLogger(TajoInterpreter.class);
  private TajoThriftClient tajoClient;


  public TajoInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    TajoConf conf = new TajoConf();
    try {
      tajoClient = new TajoThriftClient(conf, getProperty("tajo.thrift.server"), null);
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  @Override
  public void close() {
    tajoClient.close();
  }

  private int getMaxResult() {
    return Integer.parseInt(getProperty("tajo.maxResult"));
  }

  private String getHelpMessage() {
    StringBuilder builder = new StringBuilder();

    builder.append("General\n");
    builder.append("  \\version\t\tshow Tajo version\n");
    builder.append("  \\?\t\tshow help\n");
    builder.append("  \\help\t\talias of \\?\n");
    builder.append("\n");
    builder.append("Informational\n");
    builder.append("  \\l\t\tlist databases\n");
    builder.append("  \\c\t\tshow current database\n");
    builder.append("  \\c [DBNAME]\t\tconnect to new database\n");
    builder.append("  \\d\t\tlist tables\n");
    builder.append("  \\d [TBNAME]\t\tdescribe tables\n");

    return builder.toString();
  }

  private String listToOutput(List<String> list) {
    return listToOutput(list, "");
  }

  private String listToOutput(List<String> list, String defaultValue) {
    String out = "";
    if (list == null || list.size() == 0) return defaultValue;
    for (String l : list) {
      out += l + "\n";
    }
    return out;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    ResultSet result;

    logger.info("interpret function");
    if (st != null && st.trim().startsWith("\\")) {
      String tcmd = st.trim();
      try {
        if (tcmd.equals("\\l")) {       // list database
          return new InterpreterResult(
              Code.SUCCESS,
              listToOutput(tajoClient.getAllDatabaseNames()));
        } else if (tcmd.equals("\\c")) {  // show current database
          return new InterpreterResult(
              Code.SUCCESS,
              tajoClient.getCurrentDatabase());
        } else if (tcmd.startsWith("\\c")) {
          String dbName = tcmd.split(" ")[1];
          return new InterpreterResult(
              Code.SUCCESS,
              (tajoClient.selectDatabase(dbName) ?
                  "connected to database " + dbName :
                  "failed"));
        } else if (tcmd.equals("\\d")) {  // list tables
          return new InterpreterResult(
              Code.SUCCESS,
              listToOutput(
                  tajoClient.getTableList(tajoClient.getCurrentDatabase()),
                  "No Relation Found"));
        } else if (tcmd.startsWith("\\d")) { // describe tables
          String tableName = tcmd.split(" ")[1];
          TTableDesc tdesc = tajoClient.getTableDesc(tableName);

          if (tdesc == null) {
            return new InterpreterResult(
                Code.SUCCESS,
                "no such a table:" + tableName);
          }

          String out = "";
          out += "table name: " + tdesc.getTableName() + "\n";
          out += "table path: " + tdesc.getPath() + "\n";
          out += "store type: " + tdesc.getStoreType() + "\n";
          out += "number of rows: " + tdesc.getStats().getNumRows() + "\n";
          out += "volumne: " + tdesc.getStats().getNumBytes() + " B\n";
          out += "Options:\n";
          Map<String, String> meta = tdesc.getTableMeta();
          if (meta != null) {
            for (String k : meta.keySet()) {
              out += k + "\t" + meta.get(k) + "\n";
            }
          }

          out += "\n";
          out += "schema:\n";
          TSchema schema = tdesc.getSchema();
          for (TColumn col : schema.getColumns()) {
            out += col.getSimpleName() + "\t" + col.getDataTypeName() + "\n";
          }
          return new InterpreterResult(
              Code.SUCCESS,
              out);
        } else if (tcmd.startsWith("\\set")) {
          String[] set = tcmd.split(" ");
          if (set.length != 3) {
            return new InterpreterResult(
                Code.SUCCESS,
                "usage: \\set [[NAME] VALUE]");
          }

          String name = set[1];
          String value = set[2];

          if (tajoClient.updateSessionVariable(name, value)) {
            return new InterpreterResult(
                Code.SUCCESS,
                name + " = " + value);
          } else {
            return new InterpreterResult(
                Code.ERROR,
                "Error");
          }
        } else if (tcmd.startsWith("\\unset")) {
          String[] set = tcmd.split(" ");
          if (set.length != 2) {
            return new InterpreterResult(
                Code.SUCCESS,
                "usage: \\unset NAME");
          }

          String name = set[1];

          if (tajoClient.unsetSessionVariable(name)) {
            return new InterpreterResult(
                Code.SUCCESS,
                "unset " + name);
          } else {
            return new InterpreterResult(
                Code.ERROR,
                "Error");
          }
        } else {
          return new InterpreterResult(
              Code.SUCCESS,
              getHelpMessage());
        }
      } catch (Exception e) {
        throw new InterpreterException(e);
      }
    }

    try {
      result = tajoClient.executeQueryAndGetResult(st);
      // empty result
      if (result == null) {
        return new InterpreterResult(Code.SUCCESS, "");
      }

      String m = "";

      // extract column info
      ResultSetMetaData md = result.getMetaData();
      int numColumns = md.getColumnCount();

      if (numColumns == 0) {
        return new InterpreterResult(Code.SUCCESS, "");
      }

      for (int i = 1; i <= numColumns; i++) {
        if (i != 1) {
          m += "\t";
        }
        m += md.getColumnName(i);
      }
      m += "\n";

      int maxResult = getMaxResult();
      int currentRow = 0;
      String extraMessage = "";

      while (result.next()) {

        if (currentRow == maxResult) {
          extraMessage = "\n<font color=red>Results are limited by " + maxResult + ".</font>";
          break;
        }
        currentRow++;

        for (int i = 1; i <= numColumns; i++) {
          if (i != 1) {
            m += "\t";
          }

          Object col = result.getObject(i);
          if (col == null) {
            m += "\t";
          } else {
            m += col.toString();
          }
        }

        m += "\n";
      }

      return new InterpreterResult(Code.SUCCESS, "%table " + m + extraMessage);

    } catch (ServiceException | IOException | SQLException e) {
      logger.error("Error", e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }


  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {

    int result = 0;
    logger.info("Getting progress information...");
    try {
      List<TBriefQueryInfo> queryList = tajoClient.getQueryList();

      logger.info("\t " + queryList.size() + " queries");
      if (queryList.isEmpty()) {
        return result;
      }

      String queryId = queryList.get(queryList.size() - 1).queryId;
      TGetQueryStatusResponse query = tajoClient.getQueryStatus(queryId);
      logger.info("Done! " + query);
      result = (int) query.progress * 100;

    } catch (Exception e) {
      logger.error("getProgress failed ", e);
    }
    return result;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return new LinkedList<String>();
  }

}
