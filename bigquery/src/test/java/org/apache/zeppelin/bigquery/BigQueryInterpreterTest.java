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

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;

public class BigQueryInterpreterTest {
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

  public BigQueryInterpreterTest()
      throws JsonSyntaxException, JsonIOException, FileNotFoundException {
    if (constants == null) {
      InputStream is = this.getClass().getResourceAsStream("/constants.json");
      constants = (new Gson()).<Constants>fromJson(new InputStreamReader(is), Constants.class);
    }
  }

  private InterpreterGroup intpGroup;
  private BigQueryInterpreter bqInterpreter;

  private InterpreterContext context;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.bigquery.project_id", constants.getProjectId());
    p.setProperty("zeppelin.bigquery.wait_time", "5000");
    p.setProperty("zeppelin.bigquery.max_no_of_rows", "100");

    intpGroup = new InterpreterGroup();

    bqInterpreter = new BigQueryInterpreter(p);
    bqInterpreter.setInterpreterGroup(intpGroup);
    bqInterpreter.open();
  }

  @Test
  public void sqlSuccess() {
    InterpreterResult ret = bqInterpreter.interpret(constants.getOne(), context);

    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().get(0).getType(), InterpreterResult.Type.TABLE);
  }

  @Test
  public void badSqlSyntaxFails() {
    InterpreterResult ret = bqInterpreter.interpret(constants.getWrong(), context);

    assertEquals(InterpreterResult.Code.ERROR, ret.code());
  }
}
