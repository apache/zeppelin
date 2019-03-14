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

package org.apache.zeppelin.submarine;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.apache.zeppelin.submarine.commons.SubmarineConstants.OPERATION_TYPE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.ZEPPELIN_SUBMARINE_AUTH_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubmarineInterpreterTest extends BaseInterpreterTest {
  private static Logger LOGGER = LoggerFactory.getLogger(SubmarineInterpreterTest.class);

  private SubmarineInterpreter submarineIntp;

  @Override
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty(ZEPPELIN_SUBMARINE_AUTH_TYPE, "simple");
    properties.setProperty("zeppelin.python.useIPython", "false");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    properties.setProperty(SubmarineConstants.SUBMARINE_HADOOP_KEYTAB, "keytab");
    properties.setProperty(SubmarineConstants.SUBMARINE_HADOOP_PRINCIPAL, "user");

    submarineIntp = new SubmarineInterpreter(properties);

    InterpreterContext.set(getIntpContext());
    submarineIntp.open();
  }

  @Test
  public void testDashboard() throws InterpreterException {
    String script = "dashboard";
    InterpreterContext intpContext = getIntpContext();

    InterpreterResult interpreterResult = submarineIntp.interpret(script, intpContext);
    String message = interpreterResult.toJson();
    LOGGER.info(message);

    assertEquals(interpreterResult.code(), InterpreterResult.Code.SUCCESS);
    assertTrue(intpContext.out().size() >= 2);

    String dashboardTemplate = intpContext.out().getOutputAt(0).toString();
    LOGGER.info(dashboardTemplate);
    assertTrue("Did not generate template!", (dashboardTemplate.length() > 500));
  }

  @Test
  public void testJobRun() throws InterpreterException {
    String script = "JOB_RUN";
    InterpreterContext intpContext = getIntpContext();

    intpContext.getAngularObjectRegistry().add(OPERATION_TYPE, "JOB_RUN", "noteId", "paragraphId");

    InterpreterResult interpreterResult = submarineIntp.interpret(script, intpContext);
    String message = interpreterResult.toJson();
    LOGGER.info(message);

    assertEquals(interpreterResult.code(), InterpreterResult.Code.SUCCESS);
    assertTrue(intpContext.out().size() >= 2);
    String template = intpContext.out().getOutputAt(0).toString();
    assertTrue("Did not generate template!", (template.length() > 500));

    SubmarineJob job = submarineIntp.getSubmarineContext().getSubmarineJob("noteId");
    int loop = 10;
    while (loop-- > 0 && !job.getRunning()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    LOGGER.info("job.getRunning() = " + job.getRunning());
  }

  @Override
  public void tearDown() throws InterpreterException {
    submarineIntp.close();
  }
}
