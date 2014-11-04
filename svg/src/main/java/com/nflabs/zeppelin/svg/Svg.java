/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 */
package com.nflabs.zeppelin.svg;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * SVG interpreter for Zeppelin
 * 
 * @author anthonycorbacho
 *
 */
public class Svg extends Interpreter {
  private static final Logger LOG = LoggerFactory.getLogger(Svg.class);

  static {
    Interpreter.register("svg", Svg.class.getName());
  }

  public Svg(Properties property) {
    super(property);
  }

  @Override
  public void open() {}

  @Override
  public void close() {}

  @Override
  public Object getValue(String name) {
    return null;
  }

  @Override
  public InterpreterResult interpret(String svgCode) {
    LOG.info("run SVG interpreter");
    return new InterpreterResult(Code.SUCCESS, "%svg " + svgCode);
  }

  @Override
  public void cancel() {}

  @Override
  public void bindValue(String name, Object o) {}

  @Override
  public FormType getFormType() {
    return null;
  }

  @Override
  public int getProgress() {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return Collections.emptyList();
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        Svg.class.getName() + this.hashCode(), 5);
  }

}
