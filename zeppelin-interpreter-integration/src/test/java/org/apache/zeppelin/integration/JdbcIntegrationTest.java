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

package org.apache.zeppelin.integration;

import com.google.common.collect.Lists;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JdbcIntegrationTest {

  private static MiniZeppelin zeppelin;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;


  @BeforeClass
  public static void setUp() throws IOException {
    zeppelin = new MiniZeppelin();
    zeppelin.start();
    interpreterFactory = zeppelin.getInterpreterFactory();
    interpreterSettingManager = zeppelin.getInterpreterSettingManager();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    if (zeppelin != null) {
      zeppelin.stop();
    }
  }

  @Test
  public void testMySql() throws InterpreterException, InterruptedException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("jdbc");
    interpreterSetting.setProperty("default.driver", "com.mysql.jdbc.Driver");
    interpreterSetting.setProperty("default.url", "jdbc:mysql://localhost:3306/");
    interpreterSetting.setProperty("default.user", "root");
    Dependency dependency = new Dependency("mysql:mysql-connector-java:5.1.46");
    interpreterSetting.setDependencies(Lists.newArrayList(dependency));
    interpreterSettingManager.restart(interpreterSetting.getId());
    interpreterSetting.waitForReady(60 * 1000);
    Interpreter jdbcInterpreter = interpreterFactory.getInterpreter("user1", "note1", "jdbc", "test");
    assertNotNull("JdbcInterpreter is null", jdbcInterpreter);

    InterpreterContext context = new InterpreterContext.Builder()
            .setNoteId("note1")
            .setParagraphId("paragraph_1")
            .setAuthenticationInfo(AuthenticationInfo.ANONYMOUS)
            .build();
    InterpreterResult interpreterResult = jdbcInterpreter.interpret("show databases;", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
  }
}
