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
 *  - <li>Pandas DataFrame</li>
 * <ol>
 *
 * To run manually on such environment, use:
 * <code>
 *   mvn "-Dtest=org.apache.zeppelin.python.PythonPandasSqlInterpreterTest" test -pl python
 * </code>
 */
public class PythonPandasSqlInterpreterTest {

  private InterpreterGroup intpGroup;
  private PythonPandasSqlInterpreter sql;
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

    sql = new PythonPandasSqlInterpreter(p);
    sql.setInterpreterGroup(intpGroup);

    intpGroup.put("note", Arrays.asList(python, sql));

    context = new InterpreterContext("note", "id", "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null), null,
        new LinkedList<InterpreterContextRunner>(), new InterpreterOutput(
            new InterpreterOutputListener() {
              @Override public void onAppend(InterpreterOutput out, byte[] line) {}
              @Override public void onUpdate(InterpreterOutput out, byte[] output) {}
            }));

    //important to be last step
    sql.open();
    //it depends on python interpreter presence in the same group
  }

  //@Test
  public void sqlOverTestDataPrintsTable() {
    //given
    // `import pandas as pd` and `import numpy as np` done
    // DataFrame \w test data
    python.interpret("df2 = pd.DataFrame({ 'age'  : np.array([33, 51, 51, 34]), "+
                                          "'name' : pd.Categorical(['moon','jobs','gates','park'])})", context);


    //when
    InterpreterResult ret = sql.interpret("select name, age from test where age < 40", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(Type.TABLE, ret.type());
    assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message());

    assertEquals(InterpreterResult.Code.SUCCESS, sql.interpret("select case when name==\"aa\" then name else name end from test", context).code());
  }

  @Test
  public void badSqlSyntaxFails() {
    //when
    InterpreterResult ret = sql.interpret("select wrong syntax", context);

    //then
    assertNotNull("Interpreter returned 'null'", ret);
    //System.out.println("\nInterpreter response: \n" + ret.message());
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().length() > 0);
  }


}
