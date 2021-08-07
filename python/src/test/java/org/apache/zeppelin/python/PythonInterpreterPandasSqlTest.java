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

import com.google.common.collect.Lists;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * In order for this test to work, test env must have installed:
 * <ol>
 * - <li>Python</li>
 * - <li>NumPy</li>
 * - <li>Pandas</li>
 * - <li>PandaSql</li>
 * <ol>
 * <p>
 * To run manually on such environment, use:
 * <code>
 * mvn -Dpython.test.exclude='' test -pl python -am
 * </code>
 */
@RunWith(value = Parameterized.class)
public class PythonInterpreterPandasSqlTest {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(PythonInterpreterPandasSqlTest.class);

  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {true},
            {false}
    });
  }

  private boolean useIPython;
  private InterpreterGroup intpGroup;
  private PythonInterpreterPandasSql pandasSqlInterpreter;
  private PythonInterpreter pythonInterpreter;
  private IPythonInterpreter ipythonInterpreter;

  private InterpreterContext context;

  public PythonInterpreterPandasSqlTest(boolean useIPython) {
    this.useIPython = useIPython;
    LOGGER.info("Test PythonInterpreterPandasSqlTest while useIPython={}", useIPython);
  }

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.python", "python");
    p.setProperty("zeppelin.python.maxResult", "100");
    p.setProperty("zeppelin.python.useIPython", useIPython + "");
    p.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");

    intpGroup = new InterpreterGroup();

    context = getInterpreterContext();
    InterpreterContext.set(context);

    pythonInterpreter = new PythonInterpreter(p);
    ipythonInterpreter = new IPythonInterpreter(p);
    pandasSqlInterpreter = new PythonInterpreterPandasSql(p);

    pythonInterpreter.setInterpreterGroup(intpGroup);
    ipythonInterpreter.setInterpreterGroup(intpGroup);
    pandasSqlInterpreter.setInterpreterGroup(intpGroup);

    List<Interpreter> interpreters =
            Lists.newArrayList(pythonInterpreter, ipythonInterpreter, pandasSqlInterpreter);

    intpGroup.put("session_1", interpreters);

    pythonInterpreter.open();

    // to make sure python is running.
    InterpreterResult ret = pythonInterpreter.interpret("print(\"python initialized\")\n", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
    pandasSqlInterpreter.open();
  }

  @After
  public void afterTest() throws InterpreterException {
    pandasSqlInterpreter.close();
  }

  @Test
  public void dependenciesAreInstalled() throws InterpreterException {
    InterpreterResult ret =
        pythonInterpreter.interpret("import pandas\nimport pandasql\nimport numpy\n", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void errorMessageIfDependenciesNotInstalled() throws InterpreterException {
    context = getInterpreterContext();
    InterpreterResult ret = pandasSqlInterpreter.interpret("SELECT * from something", context);

    assertNotNull(ret);
    assertEquals(context.out.toString(), InterpreterResult.Code.ERROR, ret.code());
    if (useIPython) {
      assertTrue(context.out.toString(),
              context.out.toString().contains("no such table: something"));
    } else {
      assertTrue(ret.toString(), ret.toString().contains("no such table: something"));
    }
  }

  @Test
  public void sqlOverTestDataPrintsTable() throws IOException, InterpreterException {
    InterpreterResult ret = pythonInterpreter.interpret("import pandas as pd\nimport numpy as np", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // DataFrame df2 \w test data
    ret = pythonInterpreter.interpret("df2 = pd.DataFrame({ 'age'  : np.array([33, 51, 51, 34]), " +
        "'name' : pd.Categorical(['moon','jobs','gates','park'])})", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    //when
    context = getInterpreterContext();
    ret = pandasSqlInterpreter.interpret("select name, age from df2 where age < 40", context);

    //then
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(context.out.toString(), Type.TABLE,
            context.out.toInterpreterResultMessage().get(0).getType());
    assertTrue(context.out.toString().indexOf("moon\t33") > 0);
    assertTrue(context.out.toString().indexOf("park\t34") > 0);

    assertEquals(InterpreterResult.Code.SUCCESS,
        pandasSqlInterpreter.interpret(
                "select case when name==\"aa\" then name else name end from df2",
                context).code());
  }

  @Test
  public void testInIPython() throws IOException, InterpreterException {
    InterpreterResult ret =
            pythonInterpreter.interpret("import pandas as pd\nimport numpy as np", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
    // DataFrame df2 \w test data
    ret = pythonInterpreter.interpret("df2 = pd.DataFrame({ 'age'  : np.array([33, 51, 51, 34]), " +
            "'name' : pd.Categorical(['moon','jobs','gates','park'])})", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    //when
    ret = pandasSqlInterpreter.interpret("select name, age from df2 where age < 40", context);

    //then
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(context.out.toString(), Type.TABLE,
            context.out.toInterpreterResultMessage().get(1).getType());
    assertTrue(context.out.toString().indexOf("moon\t33") > 0);
    assertTrue(context.out.toString().indexOf("park\t34") > 0);

    assertEquals(InterpreterResult.Code.SUCCESS,
            pandasSqlInterpreter.interpret(
                    "select case when name==\"aa\" then name else name end from df2",
                    context).code());
  }

  @Test
  public void badSqlSyntaxFails() throws InterpreterException {
    //when
    context = getInterpreterContext();
    InterpreterResult ret = pandasSqlInterpreter.interpret("select wrong syntax", context);

    //then
    assertNotNull("Interpreter returned 'null'", ret);
    assertEquals(context.out.toString(), InterpreterResult.Code.ERROR, ret.code());
  }

  @Test
  public void showDataFrame() throws IOException, InterpreterException {
    pythonInterpreter.interpret("import pandas as pd", context);
    pythonInterpreter.interpret("import numpy as np", context);

    // given a Pandas DataFrame with an index and non-text data
    pythonInterpreter.interpret(
            "index = pd.Index([10, 11, 12, 13], name='index_name')", context);
    pythonInterpreter.interpret(
            "d1 = {1 : [np.nan, 1, 2, 3], 'two' : [3., 4., 5., 6.7]}", context);
    InterpreterResult ret = pythonInterpreter.interpret(
            "df1 = pd.DataFrame(d1, index=index)", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // when
    context = getInterpreterContext();
    ret = pythonInterpreter.interpret("z.show(df1, show_index=True)", context);

    // then
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(context.out.toString(), Type.TABLE,
            context.out.toInterpreterResultMessage().get(0).getType());
    assertTrue(context.out.toString().contains("index_name"));
    assertTrue(context.out.toString().contains("nan"));
    assertTrue(context.out.toString().contains("6.7"));
  }

  private InterpreterContext getInterpreterContext() {
    return InterpreterContext.builder()
            .setNoteId("noteId")
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput())
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .build();
  }
}
