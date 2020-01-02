/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.r;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * One shiny Interpreter can host more than one Shiny app.
 * They are organized by app name which you specify by paragraph local properties.
 * e.g.  %shiny(app=app_1)
 *
 * If you don't specify 'app', then default app name 'default' will be used.
 *
 * One shiny app is composed of at least 3 paragraph (last one is optional)
 * <p>
 *   <ul>
 *     <li>UI paragraph  e.g. %r.shiny(type=ui) </li>
 *     <li>Server paragraph e.g. %r.shiny(type=server) </li>
 *     <li>Run paragraph e.g. %r.shiny(type=run) </li>
 *     <li>Normal R code paragraph(optional) e.g. %r.shiny </li>
 *   </ul>
 * <p>
 */
public class ShinyInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShinyInterpreter.class);

  private static final String DEFAULT_APP_NAME = "default";
  private Map<String, IRInterpreter> shinyIRInterpreters = new HashMap<>();
  private RZeppelinContext z;

  public ShinyInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    this.z = new RZeppelinContext(getInterpreterGroup().getInterpreterHookRegistry(), 1000);
  }

  @Override
  public void close() throws InterpreterException {
    for (Map.Entry<String,IRInterpreter> entry : shinyIRInterpreters.entrySet()) {
      LOGGER.info("Closing IRInterpreter: " + entry.getKey());
      // Stop shiny app first otherwise the R process can not be terminated.
      entry.getValue().cancel(InterpreterContext.get());
      entry.getValue().close();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    String shinyApp = context.getStringLocalProperty("app", DEFAULT_APP_NAME);
    IRInterpreter irInterpreter = getIRInterpreter(shinyApp);
    irInterpreter.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public BaseZeppelinContext getZeppelinContext() {
    return this.z;
  }

  @Override
  public InterpreterResult internalInterpret(String st, InterpreterContext context)
          throws InterpreterException {
    String shinyApp = context.getStringLocalProperty("app", DEFAULT_APP_NAME);
    String shinyType = context.getStringLocalProperty("type", "");
    IRInterpreter irInterpreter = getIRInterpreter(shinyApp);
    if (StringUtils.isBlank(shinyType)) {
      return irInterpreter.internalInterpret(st, context);
    } else if (shinyType.equals("run")) {
      try {
        return irInterpreter.runShinyApp(context);
      } catch (IOException e) {
        throw new InterpreterException(e);
      }
    } else if (shinyType.equals("ui")) {
      return irInterpreter.shinyUI(st, context);
    } else if (shinyType.equals("server")) {
      return irInterpreter.shinyServer(st, context);
    } else {
      throw new InterpreterException("Unknown shiny type: " + shinyType);
    }
  }

  /**
   * Get the specific IRInterpreter for this shinyApp.
   * One ShinyApp is owned by one IRInterpreter(R session).
   *
   * @param shinyApp
   * @return
   * @throws InterpreterException
   */
  private IRInterpreter getIRInterpreter(String shinyApp) throws InterpreterException {
    IRInterpreter irInterpreter = null;
    synchronized (shinyIRInterpreters) {
      irInterpreter = shinyIRInterpreters.get(shinyApp);
      if (irInterpreter == null) {
        irInterpreter = new IRInterpreter(properties);
        irInterpreter.setInterpreterGroup(getInterpreterGroup());
        irInterpreter.open();
        shinyIRInterpreters.put(shinyApp, irInterpreter);
      }
    }
    return irInterpreter;
  }

}
