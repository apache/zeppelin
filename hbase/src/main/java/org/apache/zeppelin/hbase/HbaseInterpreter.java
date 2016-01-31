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

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.jruby.embed.LocalContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jruby.embed.ScriptingContainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Support for Hbase Shell. All the commands documented here
 * http://hbase.apache.org/book.html#shell is supported.
 *
 * Requirements:
 * Hbase Shell should be installed on the same machine. To be more specific, the following dir.
 * should be available: https://github.com/apache/hbase/tree/master/hbase-shell/src/main/ruby
 * Hbase Shell should be able to connect to the Hbase cluster from terminal. This makes sure
 * that the client is configured properly.
 *
 * The interpreter takes 3 config parameters:
 * hbase.home: Root dir. where hbase is installed. Default is /usr/lib/hbase/
 * hbase.ruby.sources: Dir where shell ruby code is installed.
 *                          Path is relative to hbase.home. Default: lib/ruby
 * hbase.irb.load: (Testing only) Default is true.
 *                      Whether to load irb in the interpreter.
 */
public class HbaseInterpreter extends Interpreter {
  private Logger logger = LoggerFactory.getLogger(HbaseInterpreter.class);
  private ScriptingContainer scriptingContainer;

  private StringWriter writer;

  static {
    Interpreter.register("hbase", "hbase", HbaseInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("hbase.home", "/usr/lib/hbase/", "Installation dir. of Hbase")
            .add("hbase.ruby.sources", "lib/ruby",
                "Path to Ruby scripts relative to 'hbase.home'")
            .add("hbase.test.mode", "false", "Disable checks for unit and manual tests")
            .build());
  }

  public HbaseInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    this.scriptingContainer  = new ScriptingContainer(LocalContextScope.SINGLETON);
    this.writer = new StringWriter();
    scriptingContainer.setOutput(this.writer);

    if (!Boolean.parseBoolean(getProperty("hbase.test.mode"))) {
      String hbase_home = getProperty("hbase.home");
      String ruby_src = getProperty("hbase.ruby.sources");
      Path abs_ruby_src = Paths.get(hbase_home, ruby_src).toAbsolutePath();

      logger.info("Home:" + hbase_home);
      logger.info("Ruby Src:" + ruby_src);

      File f = abs_ruby_src.toFile();
      if (!f.exists() || !f.isDirectory()) {
        throw new InterpreterException("hbase ruby sources is not available at '" + abs_ruby_src
            + "'");
      }

      logger.info("Absolute Ruby Source:" + abs_ruby_src.toString());
      // hirb.rb:41 requires the following system property to be set.
      Properties sysProps = System.getProperties();
      sysProps.setProperty("hbase.ruby.sources", abs_ruby_src.toString());

      Path abs_hirb_path = Paths.get(hbase_home, "bin/hirb.rb");
      try {
        FileInputStream fis = new FileInputStream(abs_hirb_path.toFile());
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
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
