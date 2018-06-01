/*
* Copyright 2016 Google Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.bigquery;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * The tests in this class verify that z-variable interpolation
 * and '{{...}}' escaping are disabled by default.
 */
public class BigQueryInterpolationDefaultTest {

  protected static class Constants {
    private String projectId;
    private String oneQuery;
    private String wrongQuery;

    public String getProjectId() {
      return projectId;
    }

    public String getOne() {
      return oneQuery;
    }

    public String getWrong()  {
      return wrongQuery;
    }
  }

  protected static Constants constants = null;

  public BigQueryInterpolationDefaultTest() throws JsonSyntaxException,
        JsonIOException {
    if (constants == null) {
      InputStream is = this.getClass().getResourceAsStream("/constants.json");
      constants = (new Gson()).<Constants>fromJson(new InputStreamReader(is), Constants.class);
    }
  }

  private InterpreterGroup intpGroup;
  private BigQueryInterpreter bqInterpreter;

  private InterpreterContext context;
  private InterpreterContext contextWithResourcePool;

  @Before
  public void setUp() throws Exception {
    ResourcePool resourcePool = new LocalResourcePool("BigQueryInterpolationTest");
    resourcePool.put("one", 1);
    resourcePool.put("two", 2);

    contextWithResourcePool = new InterpreterContext("", "1", null, "", "",
        new AuthenticationInfo("testUser"), null, null, null, null,
        resourcePool, null, null);

    intpGroup = new InterpreterGroup();

    Properties p = new Properties();
    p.setProperty("zeppelin.bigquery.project_id", constants.getProjectId());
    p.setProperty("zeppelin.bigquery.wait_time", "5000");
    p.setProperty("zeppelin.bigquery.max_no_of_rows", "100");
    p.setProperty("zeppelin.bigquery.sql_dialect", "");

    bqInterpreter = new BigQueryInterpreter(p);
    bqInterpreter.setInterpreterGroup(intpGroup);
    bqInterpreter.open();
  }

  @Test
  public void testInterpolationDisabledByDefault() {
    InterpreterResult ret = bqInterpreter.
        interpret("SELECT '{one}' AS col1, '{two}' AS col2", contextWithResourcePool);
    String[] lines = ret.message().get(0).getData().split("\\n");
    assertEquals(2, lines.length);
    assertEquals("col1\tcol2", lines[0]);
    assertEquals("{one}\t{two}", lines[1]);
  }

  @Test
  public void testEscapingDisabledByDefault() {
    InterpreterResult ret = bqInterpreter.
        interpret("SELECT '{{one}}' AS col1, '{{two}}' AS col2", contextWithResourcePool);
    String[] lines = ret.message().get(0).getData().split("\\n");
    assertEquals(2, lines.length);
    assertEquals("col1\tcol2", lines[0]);
    assertEquals("{{one}}\t{{two}}", lines[1]);
  }
}
