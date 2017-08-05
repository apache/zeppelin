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

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TableDataProxyTest {
  private LocalResourcePool pool;

  @Before
  public void setUp() {
    pool = new LocalResourcePool("p1");
  }

  @Test
  public void testProxyTable() {
    InterpreterResultMessage msg = new InterpreterResultMessage(
        InterpreterResult.Type.TABLE,
        "key\tvalue\nsun\t100\nmoon\t200\n");
    InterpreterResultTableData table = new InterpreterResultTableData(msg);

    pool.put("table", table);
    TableDataProxy proxy = new TableDataProxy(pool.get("table"));

    ColumnDef[] cols = proxy.columns();
    assertEquals(2, cols.length);

    assertEquals("key", cols[0].name());
    assertEquals("value", cols[1].name());

    Iterator<Row> it = proxy.rows();
    Row row = it.next();
    assertEquals(2, row.get().length);
    assertEquals("sun", row.get()[0]);
    assertEquals("100", row.get()[1]);

    row = it.next();
    assertEquals("moon", row.get()[0]);
    assertEquals("200", row.get()[1]);

    assertFalse(it.hasNext());
  }
}
