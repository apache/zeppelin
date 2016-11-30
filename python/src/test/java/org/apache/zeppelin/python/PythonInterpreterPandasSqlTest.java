/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.python;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

/**
 * In order for this test to work, test env must have installed:
 * <ol>
 *  - <li>Python</li>
 *  - <li>NumPy</li>
 *  - <li>Pandas</li>
 *  - <li>PandaSql</li>
 * <ol>
 *
 * To run manually on such environment, use:
 * <code>
 *   mvn -Dpython.test.exclude='' test -pl python -am
 * </code>
 */
public class PythonInterpreterPandasSqlTest {

  private InterpreterGroup intpGroup;
  private PythonInterpreterPandasSql sql;
  private PythonInterpreter python;

  private InterpreterContext context;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.python", "python");
    p.setProperty("zeppelin.python.maxResult", "100");

    intpGroup = new InterpreterGroup();

    python = new PythonInterpreter(p);
    python.setInterpreterGroup(intpGroup);
    python.open();

    sql = new PythonInterpreterPandasSql(p);
    sql.setInterpreterGroup(intpGroup);

    intpGroup.put("note", Arrays.asList(python, sql));

    context = new InterpreterContext("note", "id", null, "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null), null,
        new LinkedList<InterpreterContextRunner>(), new InterpreterOutput(null));

    //important to be last step
    sql.open();
    //it depends on python interpreter presence in the same group
  }

  @Test
  public void dependenciesAreInstalled() {
    InterpreterResult ret = python.interpret("import pandas\nimport pandasql\nimport numpy\n", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void errorMessageIfDependenciesNotInstalled() {
    InterpreterResult ret;
    // given
    ret = python.interpret(
        "pysqldf = lambda q: print('Can not execute SQL as Python dependency is not installed')",
         context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // when
    ret = sql.interpret("SELECT * from something", context);

    // then
    assertNotNull(ret);
    assertEquals(ret.message().get(0).getData(), InterpreterResult.Code.SUCCESS, ret.code());
    assertTrue(ret.message().get(0).getData().contains("dependency is not installed"));
  }

  @Test
  public void sqlOverTestDataPrintsTable() {
    InterpreterResult ret;
    // given
    //String expectedTable = "name\tage\n\nmoon\t33\n\npark\t34";
    ret = python.interpret("import pandas as pd", context);
    ret = python.interpret("import numpy as np", context);
    // DataFrame df2 \w test data
    ret = python.interpret("df2 = pd.DataFrame({ 'age'  : np.array([33, 51, 51, 34]), "+
                           "'name' : pd.Categorical(['moon','jobs','gates','park'])})", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    //when
    ret = sql.interpret("select name, age from df2 where age < 40", context);

    //then
    assertEquals(ret.message().get(0).getData(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().get(0).getData(), Type.TABLE, ret.message().get(0).getType());
    //assertEquals(expectedTable, ret.message()); //somehow it's same but not equal
    assertTrue(ret.message().get(0).getData().indexOf("moon\t33") > 0);
    assertTrue(ret.message().get(0).getData().indexOf("park\t34") > 0);

    assertEquals(InterpreterResult.Code.SUCCESS, sql.interpret("select case when name==\"aa\" then name else name end from df2", context).code());
  }

  @Test
  public void badSqlSyntaxFails() {
    //when
    InterpreterResult ret = sql.interpret("select wrong syntax", context);

    //then
    assertNotNull("Interpreter returned 'null'", ret);
    //System.out.println("\nInterpreter response: \n" + ret.message());
    assertEquals(ret.toString(), InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);
  }

  @Test
  public void showDataFrame() {
    InterpreterResult ret;
    ret = python.interpret("import pandas as pd", context);
    ret = python.interpret("import numpy as np", context);

    // given a Pandas DataFrame with an index and non-text data
    ret = python.interpret("index = pd.Index([10, 11, 12, 13], name='index_name')", context);
    ret = python.interpret("d1 = {1 : [np.nan, 1, 2, 3], 'two' : [3., 4., 5., 6.7]}", context);
    ret = python.interpret("df1 = pd.DataFrame(d1, index=index)", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // when
    ret = python.interpret("z.show(df1, show_index=True)", context);

    // then
    assertEquals(ret.message().get(0).getData(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().get(0).getData(), Type.TABLE, ret.message().get(0).getType());
    assertTrue(ret.message().get(0).getData().indexOf("index_name") == 0);
    assertTrue(ret.message().get(0).getData().indexOf("13") > 0);
    assertTrue(ret.message().get(0).getData().indexOf("nan") > 0);
    assertTrue(ret.message().get(0).getData().indexOf("6.7") > 0);
  }
}
