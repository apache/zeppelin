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

package org.apache.zeppelin.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterException;
import org.junit.jupiter.api.Test;

/**
 * Tests for FileInterpreter CommandArgs parsing functionality.
 */
class FileInterpreterTest {

  /**
   * Mock FileInterpreter for testing CommandArgs functionality
   */
  private static class TestFileInterpreter extends FileInterpreter {
    TestFileInterpreter(Properties property) {
      super(property);
    }

    @Override
    public String listAll(String path) throws InterpreterException {
      return "";
    }

    @Override
    public boolean isDirectory(String path) {
      return true;
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    // Expose CommandArgs for testing
    public CommandArgs getCommandArgs(String cmd) {
      CommandArgs args = new CommandArgs(cmd);
      args.parseArgs();
      return args;
    }
  }

  @Test
  void testCommandArgsParsing() {
    TestFileInterpreter interpreter = new TestFileInterpreter(new Properties());

    // Test simple command without flags
    FileInterpreter.CommandArgs args1 = interpreter.getCommandArgs("ls");
    assertEquals("ls", args1.command);
    assertEquals(0, args1.args.size());
    assertEquals(0, args1.flags.size());

    // Test command with path
    FileInterpreter.CommandArgs args2 = interpreter.getCommandArgs("ls /user");
    assertEquals("ls", args2.command);
    assertEquals(1, args2.args.size());
    assertEquals("/user", args2.args.get(0));
    assertEquals(0, args2.flags.size());

    // Test command with single flag
    FileInterpreter.CommandArgs args3 = interpreter.getCommandArgs("ls -l");
    assertEquals("ls", args3.command);
    assertEquals(0, args3.args.size());
    assertEquals(1, args3.flags.size());
    assertTrue(args3.flags.contains('l'));
    assertFalse(args3.flags.contains('-'));

    // Test command with multiple flags
    FileInterpreter.CommandArgs args4 = interpreter.getCommandArgs("ls -la");
    assertEquals("ls", args4.command);
    assertEquals(0, args4.args.size());
    assertEquals(2, args4.flags.size());
    assertTrue(args4.flags.contains('l'));
    assertTrue(args4.flags.contains('a'));
    assertFalse(args4.flags.contains('-'));

    // Test command with flags and path
    FileInterpreter.CommandArgs args5 = interpreter.getCommandArgs("ls -l /user");
    assertEquals("ls", args5.command);
    assertEquals(1, args5.args.size());
    assertEquals("/user", args5.args.get(0));
    assertEquals(1, args5.flags.size());
    assertTrue(args5.flags.contains('l'));
    assertFalse(args5.flags.contains('-'));

    // Test command with separate flags
    FileInterpreter.CommandArgs args6 = interpreter.getCommandArgs("ls -l -h /user");
    assertEquals("ls", args6.command);
    assertEquals(1, args6.args.size());
    assertEquals("/user", args6.args.get(0));
    assertEquals(2, args6.flags.size());
    assertTrue(args6.flags.contains('l'));
    assertTrue(args6.flags.contains('h'));
    assertFalse(args6.flags.contains('-'));

    // Test command with combined flags
    FileInterpreter.CommandArgs args7 = interpreter.getCommandArgs("ls -lah /user");
    assertEquals("ls", args7.command);
    assertEquals(1, args7.args.size());
    assertEquals("/user", args7.args.get(0));
    assertEquals(3, args7.flags.size());
    assertTrue(args7.flags.contains('l'));
    assertTrue(args7.flags.contains('a'));
    assertTrue(args7.flags.contains('h'));
    assertFalse(args7.flags.contains('-'));
  }

  @Test
  void testCommandArgsWithDashNotInFlags() {
    TestFileInterpreter interpreter = new TestFileInterpreter(new Properties());

    // Test that dash character is not included in flags after fix
    FileInterpreter.CommandArgs args = interpreter.getCommandArgs("ls -l");
    
    // Verify dash is not in flags
    assertFalse(args.flags.contains('-'), 
        "Dash character should not be included in flags");
    
    // Verify correct flag is included
    assertTrue(args.flags.contains('l'), 
        "Flag 'l' should be included");
    
    // Verify flag count
    assertEquals(1, args.flags.size(), 
        "Should only have one flag character");
  }

  @Test
  void testEmptyFlags() {
    TestFileInterpreter interpreter = new TestFileInterpreter(new Properties());

    // Test empty flag (just dash)
    FileInterpreter.CommandArgs args = interpreter.getCommandArgs("ls -");
    assertEquals("ls", args.command);
    assertEquals(0, args.args.size());
    assertEquals(0, args.flags.size());
  }

  @Test
  void testComplexCommand() {
    TestFileInterpreter interpreter = new TestFileInterpreter(new Properties());

    // Test complex command with multiple flags and arguments
    FileInterpreter.CommandArgs args = interpreter.getCommandArgs("ls -la -h /user /tmp");
    assertEquals("ls", args.command);
    assertEquals(2, args.args.size());
    assertEquals("/user", args.args.get(0));
    assertEquals("/tmp", args.args.get(1));
    assertEquals(3, args.flags.size());
    assertTrue(args.flags.contains('l'));
    assertTrue(args.flags.contains('a'));
    assertTrue(args.flags.contains('h'));
    assertFalse(args.flags.contains('-'));
  }
}
