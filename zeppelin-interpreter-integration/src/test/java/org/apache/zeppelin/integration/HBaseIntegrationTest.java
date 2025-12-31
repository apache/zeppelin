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


import org.apache.zeppelin.test.DownloadUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class HBaseIntegrationTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(HBaseIntegrationTest.class);

  private static InterpreterFactory interpreterFactory;
  protected static InterpreterSettingManager interpreterSettingManager;

  private String hbaseHome;

  private static MiniZeppelinServer zepServer;

  public void prepareHBase(String hbaseVersion) {
    LOGGER.info("Testing HBase Version: " + hbaseVersion);
    this.hbaseHome = DownloadUtils.downloadHBase(hbaseVersion);
  }

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(HBaseIntegrationTest.class.getSimpleName());
    zepServer.addInterpreter("hbase");
    zepServer.copyBinDir();
    zepServer.copyLogProperties();
    zepServer.start();
  }

  @AfterAll
  public static void tearDown() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setup() {
    interpreterSettingManager = zepServer.getService(InterpreterSettingManager.class);
    interpreterFactory = new InterpreterFactory(interpreterSettingManager);
  }

  @Test
  public void testHBaseInterpreter() throws Exception {
    InterpreterSetting hbaseInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("hbase");
    hbaseInterpreterSetting.setProperty("hbase.home", hbaseHome);

    Interpreter hbaseInterpreter = interpreterFactory.getInterpreter("hbase", new ExecutionContext("user1", "note1", "hbase"));

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = hbaseInterpreter.interpret("puts 'hello'", context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());
    assertTrue(interpreterResult.message().get(0).getData().contains("hello"));

    interpreterSettingManager.close();
  }

}
