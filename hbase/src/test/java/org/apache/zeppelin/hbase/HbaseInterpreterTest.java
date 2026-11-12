/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.hbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;

/**
 * Tests for HBase Interpreter.
 */
public class HbaseInterpreterTest {
  private static HbaseInterpreter hbaseInterpreter;

  @BeforeAll
  public static void setUp() {
    Properties properties = new Properties();
    properties.put("HBASE_HOME", "");

    hbaseInterpreter = new HbaseInterpreter(properties);
    hbaseInterpreter.open();
  }

  @Test
  void newObject() {
    assertNotNull(hbaseInterpreter);
  }

  @Test
  void testDefaultCommandTimeout() {
    HbaseInterpreter interpreter = new HbaseInterpreter(new Properties());

    assertEquals("600000", interpreter.getProperty("shell.command.timeout.millisecs"));
  }

  @Test
  void testCommandTimeoutIsNotOverridden() {
    Properties properties = new Properties();
    properties.put("shell.command.timeout.millisecs", "1000");

    HbaseInterpreter interpreter = new HbaseInterpreter(properties);

    assertEquals("1000", interpreter.getProperty("shell.command.timeout.millisecs"));
  }

  @Test
  void testHbaseHomeFallback() {
    assertEquals("/usr/lib/hbase", new HbaseInterpreter(new Properties()).getHbaseHome());

    Properties legacyProperties = new Properties();
    legacyProperties.put("hbase.home", "/opt/hbase");
    assertEquals("/opt/hbase", new HbaseInterpreter(legacyProperties).getHbaseHome());

    Properties bothProperties = new Properties();
    bothProperties.put("hbase.home", "/opt/hbase");
    bothProperties.put("HBASE_HOME", "/usr/local/hbase");
    assertEquals("/usr/local/hbase", new HbaseInterpreter(bothProperties).getHbaseHome());

    Properties blankProperties = new Properties();
    blankProperties.put("hbase.home", "/opt/hbase");
    blankProperties.put("HBASE_HOME", " ");
    assertEquals("/opt/hbase", new HbaseInterpreter(blankProperties).getHbaseHome());
  }

  @Test
  void testShellQuote() {
    assertEquals("'list'", HbaseInterpreter.shellQuote("list"));
    assertEquals("'a'\\''b'", HbaseInterpreter.shellQuote("a'b"));
    assertEquals("'\"quoted\"'", HbaseInterpreter.shellQuote("\"quoted\""));
    assertEquals("'$100'", HbaseInterpreter.shellQuote("$100"));
  }

  @Test
  void testBuildCommand() {
    HbaseInterpreter interpreter = new HbaseInterpreter(new Properties());

    assertEquals("printf '%s\\n' 'list' | '/usr/lib/hbase/bin/hbase' shell -n",
        interpreter.buildCommand("list"));
  }

  @Test
  void testBuildCommandQuotesSpecialCharacters() {
    HbaseInterpreter interpreter = new HbaseInterpreter(new Properties());

    assertEquals("printf '%s\\n' "
            + "'scan '\\''t'\\'', {FILTER => \"PrefixFilter('\\''a'\\'')\"}'"
            + " | '/usr/lib/hbase/bin/hbase' shell -n",
        interpreter.buildCommand("scan 't', {FILTER => \"PrefixFilter('a')\"}"));

    assertEquals("printf '%s\\n' "
            + "'put '\\''t'\\'','\\''r'\\'','\\''c'\\'','\\''$100'\\'''"
            + " | '/usr/lib/hbase/bin/hbase' shell -n",
        interpreter.buildCommand("put 't','r','c','$100'"));
  }

  @Test
  void testBuildCommandQuotesHbaseHome() {
    Properties properties = new Properties();
    properties.put("HBASE_HOME", "/opt/hbase home");
    HbaseInterpreter interpreter = new HbaseInterpreter(properties);

    assertEquals("printf '%s\\n' 'list' | '/opt/hbase home/bin/hbase' shell -n",
        interpreter.buildCommand("list"));
  }

  @Test
  void testBlankCommandIsNoop() {
    InterpreterContext context = new InterpreterContext.Builder()
        .setNoteId("note1")
        .setParagraphId("paragraph_1")
        .build();

    assertEquals(InterpreterResult.Code.SUCCESS,
        hbaseInterpreter.internalInterpret("   ", context).code());
  }
}
