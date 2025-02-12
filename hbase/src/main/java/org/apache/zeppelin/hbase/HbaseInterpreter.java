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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HBase interpreter. It uses the hbase shell to interpret the commands.
 */
public class HbaseInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(HbaseInterpreter.class);

  public static final String HBASE_HOME = "hbase.home";

  private static final Path TEMP_FOLDER = Paths.get(System.getProperty("java.io.tmpdir"),
          "zeppelin-hbase-scripts");

  private Map<String, Executor> runningProcesses = new HashMap<>();

  private Map<String, File> tempFiles = new HashMap<>();

  private static final int SIGTERM_CODE = 143;

  private long commandTimeout = 60000;

  public HbaseInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    // Do nothing
  }

  @Override
  public void close() {
    runningProcesses.clear();
    runningProcesses = null;
    tempFiles.clear();
    tempFiles = null;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    LOGGER.debug("Run HBase shell script: {}", st);

    if (StringUtils.isEmpty(st)) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    String paragraphId = context.getParagraphId();
    final File scriptFile;
    try {
      // Write script in a temporary file
      // The script is enriched with extensions
      scriptFile = createTempFile(paragraphId);
      FileUtils.write(scriptFile, st + "\nexit");
    } catch (IOException e) {
      LOGGER.error("Can not write script in temp file", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    }

    InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS);

    final DefaultExecutor executor = new DefaultExecutor();
    final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

    executor.setStreamHandler(new PumpStreamHandler(context.out, errorStream));
    executor.setWatchdog(new ExecuteWatchdog(commandTimeout));

    String hbaseCmdPath = Paths.get(getProperty(HBASE_HOME), "bin", "hbase").toString();
    final CommandLine cmdLine = CommandLine.parse(hbaseCmdPath);
    cmdLine.addArgument("shell", false);
    cmdLine.addArgument(scriptFile.getAbsolutePath(), false);

    try {
      executor.execute(cmdLine);
      runningProcesses.put(paragraphId, executor);
    } catch (ExecuteException e) {
      LOGGER.error("Can not run script in paragraph {}", paragraphId, e);

      final int exitValue = e.getExitValue();
      InterpreterResult.Code code = InterpreterResult.Code.ERROR;
      String msg = errorStream.toString();

      if (exitValue == SIGTERM_CODE) {
        code = InterpreterResult.Code.INCOMPLETE;
        msg = msg + "Paragraph received a SIGTERM.\n";
        LOGGER.info("The paragraph {} stopped executing: {}", paragraphId, msg);
      }

      msg += "ExitValue: " + exitValue;
      result = new InterpreterResult(code, msg);
    } catch (IOException e) {
      LOGGER.error("Can not run script in paragraph {}", paragraphId, e);
      result = new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    } finally {
      deleteTempFile(paragraphId);
      stopProcess(paragraphId);
    }
    return result;
  }

  @Override
  public void cancel(InterpreterContext context) {
    stopProcess(context.getParagraphId());
    deleteTempFile(context.getParagraphId());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        HbaseInterpreter.class.getName() + this.hashCode());
  }

  private void stopProcess(String paragraphId) {
    Executor executor = runningProcesses.remove(paragraphId);
    if (null != executor) {
      final ExecuteWatchdog watchdog = executor.getWatchdog();
      watchdog.destroyProcess();
    }
  }

  private File createTempFile(String paragraphId) throws IOException {
    if (!Files.exists(TEMP_FOLDER)) {
      Files.createDirectory(TEMP_FOLDER);
    }
    File temp = Files.createTempFile(TEMP_FOLDER, paragraphId, ".txt").toFile();
    tempFiles.put(paragraphId, temp);
    return temp;
  }

  private void deleteTempFile(String paragraphId) {
    File tmpFile = tempFiles.remove(paragraphId);
    if (null != tmpFile) {
      FileUtils.deleteQuietly(tmpFile);
    }
  }
}
