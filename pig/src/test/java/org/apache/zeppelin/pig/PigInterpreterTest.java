/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.pig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Properties;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.StringWriter;
import org.apache.commons.io.FileUtils;
import static org.apache.zeppelin.pig.PigInterpreter.*;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import static org.junit.Assert.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PigInterpreterTest {

  private static PigInterpreter pig;
  private static InterpreterContext context;
  private static final String PASSWD_FILE = "/tmp/tmp_zeppelin_dummypasswd";
  private static final String USERS_FILE = "/tmp/tmp_zeppelin_dummyusers";

  @BeforeClass
  public static void setUp() {
    Properties properties = new Properties();
    properties.put(PIG_START_EXE, DEFAULT_START_EXE);
    properties.put(PIG_START_ARGS, DEFAULT_START_ARGS);
    properties.put(PIG_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
 
    pig = new PigInterpreter(properties);
    pig.open();

    context = new InterpreterContext(null, null, null, null, null, null, null, null);
  }

  @AfterClass
  public static void tearDown() {
    pig.close();
    pig.destroy();
    try {
      org.apache.commons.io.FileUtils.forceDelete(new File(PASSWD_FILE));
      org.apache.commons.io.FileUtils.forceDelete(new File(USERS_FILE));
    } catch (IOException e) {
       StringWriter sw = new StringWriter();
       e.printStackTrace(new PrintWriter(sw));
       fail("Unable to cleanup temp pig files:\n" + sw.toString());
    }
  }

 /**
  * Extract users from a dummy passwd file
  * https://pig.apache.org/docs/r0.10.0/start.html#run
  */
  @Test
  public void testExtractUsers() {
    PrintWriter out = null;
    StringWriter sw = new StringWriter();

    try {
      out = new PrintWriter(new File(PASSWD_FILE));
      out.println("user1:pass1");
      out.println("user2:pass2");
      out.println("user3:pass3");
      out.println("user4:pass4");
      out.flush();
    } catch (FileNotFoundException e) {
       e.printStackTrace(new PrintWriter(sw));
       fail("Unable to write to "+PASSWD_FILE+":\n" + sw.toString());
    } finally {
      if (out != null){
        out.close();
      }
    }

    try {
      FileUtils.deleteDirectory(new File(USERS_FILE));
    } catch (IOException e) {
        e.printStackTrace(new PrintWriter(sw));
        fail("Unable to delete: "+USERS_FILE+". Error: " + sw.toString());
    }
    String pigScript = "A = load 'file://"+PASSWD_FILE+"' using PigStorage(':');";
    pigScript += "B = foreach A generate $0 as id;";
    pigScript += "store B into 'file://"+USERS_FILE+"';";

    InterpreterResult result = pig.interpret(pigScript, context);

    String readusers = null;
    try {
      readusers = new String(Files.readAllBytes(Paths.get(USERS_FILE+"/part-m-00000")));
    } catch (IOException e) {
        e.printStackTrace(new PrintWriter(sw));
        fail("Unable read output of pig job from: "+USERS_FILE+"/part-m-00000. Error:\n" + sw.toString());
    }
    assertEquals(readusers, "user1\nuser2\nuser3\nuser4\n");

  }


}
