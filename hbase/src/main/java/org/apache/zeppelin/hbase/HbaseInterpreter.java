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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.shell.ShellInterpreter;

public class HbaseInterpreter extends ShellInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(HbaseInterpreter.class);

  private static final String HBASE_HOME = "HBASE_HOME";
  private static final String LEGACY_HBASE_HOME = "hbase.home";
  private static final String DEFAULT_HBASE_HOME = "/usr/lib/hbase";
  private static final String SHELL_COMMAND_TIMEOUT = "shell.command.timeout.millisecs";
  // Bound the inactivity timeout so that long-running HBase commands are not
  // killed while a hung process is still eventually cleaned up.
  private static final long DEFAULT_COMMAND_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

  public HbaseInterpreter(Properties property) {
    super(property);
    if (isBlank(getProperty(SHELL_COMMAND_TIMEOUT))) {
      setProperty(SHELL_COMMAND_TIMEOUT, String.valueOf(DEFAULT_COMMAND_TIMEOUT_MS));
    }
  }

  @Override
  public void open() {
    super.open();
    LOGGER.info("HBASE_HOME: {}", getHbaseHome());
  }

  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    if (isBlank(cmd)) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }
    String hbaseCommand = buildCommand(cmd);
    LOGGER.debug("HBase command: {}", hbaseCommand);
    return super.internalInterpret(hbaseCommand, context);
  }

  String buildCommand(String cmd) {
    // Quote the command and the hbase path so that quotes, '$', backticks and
    // spaces are passed to the hbase shell verbatim.
    return "printf '%s\\n' " + shellQuote(cmd.trim())
        + " | " + shellQuote(getHbaseHome() + "/bin/hbase") + " shell -n";
  }

  String getHbaseHome() {
    String home = getProperty(HBASE_HOME);
    if (isBlank(home)) {
      home = getProperty(LEGACY_HBASE_HOME);
    }
    if (isBlank(home)) {
      home = DEFAULT_HBASE_HOME;
    }
    return home;
  }

  static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
