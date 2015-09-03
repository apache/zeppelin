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

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.jruby.embed.LocalContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jruby.embed.ScriptingContainer;

import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * HBase Shell Interpreter. (https://wiki.apache.org/hadoop/Hbase/Shell)
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
            .add("hbase.irb.load", "true", "Load hirb. Optional for testing only")
            .build());
  }

  public HbaseInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    String hbase_home = getProperty("hbase.home");
    String ruby_src = getProperty("hbase.ruby.sources");
    String abs_ruby_src = hbase_home + ruby_src;

    logger.info("Home:" + hbase_home);
    logger.info("Ruby Src:" + ruby_src);

    Properties props = System.getProperties();
    props.setProperty("hbase.ruby.sources", abs_ruby_src);
    this.scriptingContainer  = new ScriptingContainer(LocalContextScope.SINGLETON);
    List<String> paths = new ArrayList<>(Arrays.asList(abs_ruby_src));
    this.writer = new StringWriter();
    scriptingContainer.setOutput(this.writer);
    this.scriptingContainer.setLoadPaths(paths);
    scriptingContainer.setCompatVersion(org.jruby.CompatVersion.RUBY1_9);
    if (Boolean.parseBoolean(getProperty("hbase.irb.load"))) {
      try {
        InputStream in = getClass().getResourceAsStream("/hbase/bin/hirb.rb");
        scriptingContainer.runScriptlet(in, "/hbase/bin/hirb.rb");
        in.close();
      } catch (NullPointerException | IOException e) {
        logger.error("Open failed:", e);
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
      logger.error("Can not run " + cmd, t);
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
