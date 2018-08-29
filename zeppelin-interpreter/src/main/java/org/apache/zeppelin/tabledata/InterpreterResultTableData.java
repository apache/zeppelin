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
package org.apache.zeppelin.tabledata;

import org.apache.zeppelin.interpreter.InterpreterResultMessage;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Table data with interpreter result type 'TABLE'
 */
public class InterpreterResultTableData implements TableData, Serializable {
  private final InterpreterResultMessage msg;
  ColumnDef [] columnDef;
  List<Row> rows = new LinkedList<>();

  public InterpreterResultTableData(InterpreterResultMessage msg) {
    this.msg = msg;

    String[] lines = msg.getData().split("\n");
    if (lines == null || lines.length == 0) {
      columnDef = null;
    } else {
      String[] headerRow = lines[0].split("\t");
      columnDef = new ColumnDef[headerRow.length];
      for (int i = 0; i < headerRow.length; i++) {
        columnDef[i] = new ColumnDef(headerRow[i], ColumnDef.TYPE.STRING);
      }

      for (int r = 1; r < lines.length; r++) {
        Object [] row = lines[r].split("\t");
        rows.add(new Row(row));
      }
    }
  }


  @Override
  public ColumnDef[] columns() {
    return columnDef;
  }

  @Override
  public Iterator<Row> rows() {
    return rows.iterator();
  }
}
