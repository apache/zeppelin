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
package org.apache.zeppelin.spark;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UtilsTest {

  static Logger LOGGER = LoggerFactory.getLogger(UtilsTest.class);

  @Test
  public void testLongJobDescription() {
    String paracode = "paraText";

    // interpretertype and code on same line
    String paraText = "%spark " + paracode;
    String jobLongText = Utils.getJobLongText(paraText);
    assertEquals(paracode, jobLongText);
    LOGGER.info("Test passed with interpretertype and code on same line ");

    // Interpreter type and code in different lines
    paraText = "%spark\n" + paracode;
    jobLongText = Utils.getJobLongText(paraText);
    assertEquals(paracode, jobLongText);
    LOGGER.info("Test passed with interpretertype and code on same line ");

    // Only code(uses default interpreter type from bindings)
    paraText = paracode;
    jobLongText = Utils.getJobLongText(paraText);
    assertEquals(paracode, jobLongText);
    LOGGER.info("Test passed with no explicit interpreter type");
  }

  @Test
  public void testShortJobDescription() {
    String paracodeFirstLine = "paraText1";
    String paracode = paracodeFirstLine + "\nparatext2";

    // interpretertype and code on same line
    String paraText = "%spark " + paracode;
    String jobShortText = Utils.getJobShortText(paraText);
    assertEquals(paracodeFirstLine, jobShortText);
    LOGGER.info("Test passed with interpretertype and code on same line ");

    // Interpreter type and code in different lines
    paraText = "%spark\n" + paracode;
    jobShortText = Utils.getJobShortText(paraText);
    assertEquals(paracodeFirstLine, jobShortText);
    LOGGER.info("Test passed with Interpreter type and code in different lines");

    // Only code(uses first interpreter type from bindings)
    paraText = paracode;
    jobShortText = Utils.getJobShortText(paraText);
    assertEquals(paracodeFirstLine, jobShortText);
    LOGGER.info("Test passed with no explicit interpreter type");
  }
}
