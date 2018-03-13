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

import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

/**
 * Support for HBase Shell. All the commands documented here
 * http://hbase.apache.org/book.html#shell is supported.
 *
 * Requirements:
 * HBase Shell should be installed on the same machine. To be more specific, the following dir.
 * should be available: https://github.com/apache/hbase/tree/master/hbase-shell/src/main/ruby
 * HBase Shell should be able to connect to the HBase cluster from terminal. This makes sure
 * that the client is configured properly.
 *
 * The interpreter takes 3 config parameters:
 * hbase.home: Root directory where HBase is installed. Default is /usr/lib/hbase/
 * hbase.ruby.sources: Dir where shell ruby code is installed.
 *                          Path is relative to hbase.home. Default: lib/ruby
 * zeppelin.hbase.test.mode: (Testing only) Disable checks for unit and manual tests. Default: false
 */
public class HbaseInterpreter extends Interpreter {
  public static final String HBASE_HOME = "hbase.home";
  public static final String HBASE_RUBY_SRC = "hbase.ruby.sources";
  public static final String HBASE_TEST_MODE = "zeppelin.hbase.test.mode";

  private Logger logger = LoggerFactory.getLogger(HbaseInterpreter.class);
  private ScriptingContainer scriptingContainer;

  private StringWriter writer;

  public HbaseInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    this.scriptingContainer  = new ScriptingContainer(LocalContextScope.SINGLETON);
    this.writer = new StringWriter();
    scriptingContainer.setOutput(this.writer);

    if (!Boolean.parseBoolean(getProperty(HBASE_TEST_MODE))) {
      String hbaseHome = getProperty(HBASE_HOME);
      String rubySrc = getProperty(HBASE_RUBY_SRC);
      Path absRubySrc = Paths.get(hbaseHome, rubySrc).toAbsolutePath();

      logger.info("Home:" + hbaseHome);
      logger.info("Ruby Src:" + rubySrc);

      File f = absRubySrc.toFile();
      if (!f.exists() || !f.isDirectory()) {
        throw new InterpreterException("HBase ruby sources is not available at '" + absRubySrc
            + "'");
      }

      logger.info("Absolute Ruby Source:" + absRubySrc.toString());
      // hirb.rb:41 requires the following system properties to be set.
      Properties sysProps = System.getProperties();
      sysProps.setProperty(HBASE_RUBY_SRC, absRubySrc.toString());

      Path absHirbPath = Paths.get(hbaseHome, "bin/hirb.rb");
      try {
        FileInputStream fis = new FileInputStream(absHirbPath.toFile());
        this.scriptingContainer.runScriptlet(fis, "hirb.rb");
        fis.close();
      } catch (IOException e) {
        throw new InterpreterException(e.getCause());
      }
    }
  }

  @Override
  public void close() {
    if (this.scriptingContainer != null) {
      this.scriptingContainer.terminate();
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext interpreterContext) {
    try {
      logger.info(cmd);
      this.writer.getBuffer().setLength(0);
      this.scriptingContainer.runScriptlet(cmd);
      this.writer.flush();
      logger.debug(writer.toString());
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, writer.getBuffer().toString());
    } catch (Throwable t) {
      logger.error("Can not run '" + cmd + "'", t);
      return new InterpreterResult(InterpreterResult.Code.ERROR, t.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {}

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

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return null;
  }

  private static String getSystemDefault(
      String envName,
      String propertyName,
      String defaultValue) {

    if (envName != null && !envName.isEmpty()) {
      String envValue = System.getenv().get(envName);
      if (envValue != null) {
        return envValue;
      }
    }

    if (propertyName != null && !propertyName.isEmpty()) {
      String propValue = System.getProperty(propertyName);
      if (propValue != null) {
        return propValue;
      }
    }
    return defaultValue;
  }
}
