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

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;

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

  @SuppressWarnings("checkstyle:abbreviationaswordinname")
  protected static Constants CONSTANTS = null;

  public BigQueryInterpreterTest()
      throws JsonSyntaxException, JsonIOException, FileNotFoundException {
    if (CONSTANTS == null) {
      InputStream is = this.getClass().getResourceAsStream("/constants.json");
      CONSTANTS = (new Gson()).<Constants>fromJson(new InputStreamReader(is), Constants.class);
    }
  }

  private InterpreterGroup intpGroup;
  private BigQueryInterpreter bqInterpreter;

  private InterpreterContext context;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.bigquery.project_id", CONSTANTS.getProjectId());
    p.setProperty("zeppelin.bigquery.wait_time", "5000");
    p.setProperty("zeppelin.bigquery.max_no_of_rows", "100");

    intpGroup = new InterpreterGroup();

    bqInterpreter = new BigQueryInterpreter(p);
    bqInterpreter.setInterpreterGroup(intpGroup);
    bqInterpreter.open();

  }

  @Test
  public void sqlSuccess() {
    InterpreterResult ret = bqInterpreter.interpret(CONSTANTS.getOne(), context);

    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().get(0).getType(), InterpreterResult.Type.TABLE);

  }

  @Test
  public void badSqlSyntaxFails() {
    InterpreterResult ret = bqInterpreter.interpret(CONSTANTS.getWrong(), context);

    assertEquals(InterpreterResult.Code.ERROR, ret.code());
  }

}
