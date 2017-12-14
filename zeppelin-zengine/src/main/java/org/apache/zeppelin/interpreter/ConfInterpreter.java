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

package org.apache.zeppelin.interpreter;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * Special Interpreter for Interpreter Configuration customization. It is attached to each
 * InterpreterGroup implicitly by Zeppelin.
 */
public class ConfInterpreter extends Interpreter {

  private static Logger LOGGER = LoggerFactory.getLogger(ConfInterpreter.class);

  private String interpreterGroupId;
  private InterpreterSetting interpreterSetting;


  public ConfInterpreter(Properties properties,
                         String interpreterGroupId,
                         InterpreterSetting interpreterSetting) {
    super(properties);
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterSetting = interpreterSetting;
  }

  @Override
  public void open() throws InterpreterException {

  }

  @Override
  public void close() throws InterpreterException {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {

    try {
      Properties finalProperties = new Properties();
      finalProperties.putAll(getProperties());
      Properties newProperties = new Properties();
      newProperties.load(new StringReader(st));
      finalProperties.putAll(newProperties);
      LOGGER.debug("Properties for InterpreterGroup: " + interpreterGroupId + " is "
          + finalProperties);
      interpreterSetting.setInterpreterGroupProperties(interpreterGroupId, finalProperties);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    } catch (IOException e) {
      LOGGER.error("Fail to update interpreter setting", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {

  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return null;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }
}
