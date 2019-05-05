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

package org.apache.zeppelin.submarine;

import com.google.common.io.Resources;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SubmarineJobTest {
  private static Logger LOGGER = LoggerFactory.getLogger(SubmarineJobTest.class);

  private final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
  private final String shell = isWindows ? "cmd /c" : "bash -c";

  private static final String DEFAULT_EXECUTOR_TEST = "DefaultExecutorTest.sh";

  @Test
  public void defaultExecutorTest() throws IOException {
    DefaultExecutor executor = new DefaultExecutor();
    CommandLine cmdLine = CommandLine.parse(shell);

    URL urlTemplate = Resources.getResource(DEFAULT_EXECUTOR_TEST);

    cmdLine.addArgument(urlTemplate.getFile(), false);

    Map<String, String> env = new HashMap<>();
    env.put("CLASSPATH", "`$HADOOP_HDFS_HOME/bin/hadoop classpath --glob`");
    env.put("EVN", "test");

    AtomicBoolean cmdLineRunning = new AtomicBoolean(true);
    StringBuffer sbLogOutput = new StringBuffer();
    executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
      @Override
      protected void processLine(String line, int level) {
        //LOGGER.info(line);
        sbLogOutput.append(line + "\n");
      }
    }));

    executor.execute(cmdLine, env, new DefaultExecuteResultHandler() {
      @Override
      public void onProcessComplete(int exitValue) {
        cmdLineRunning.set(false);
      }
      @Override
      public void onProcessFailed(ExecuteException e) {
        cmdLineRunning.set(false);
        LOGGER.error(e.getMessage());
      }
    });
    int loopCount = 100;
    while ((loopCount-- > 0) && cmdLineRunning.get()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    LOGGER.info(sbLogOutput.toString());
  }
}
