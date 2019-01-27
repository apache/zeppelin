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

package org.apache.zeppelin.service;

import static org.junit.Assert.assertTrue;

import org.apache.log4j.Level;
import org.junit.Test;

public class AdminServiceTest {

  @Test
  public void testSetLoggerLevel() {
    AdminService adminService = new AdminService();
    String testLoggerName = "test";
    org.apache.log4j.Logger logger = adminService.getLogger(testLoggerName);
    org.apache.log4j.Level level = logger.getLevel();
    boolean setInfo = false;
    if (org.apache.log4j.Level.INFO == level) {
      // if a current level is INFO, set DEBUG to check if it's changed or not
      logger.setLevel(org.apache.log4j.Level.DEBUG);
    } else {
      logger.setLevel(org.apache.log4j.Level.INFO);
      setInfo = true;
    }

    logger = adminService.getLogger(testLoggerName);
    assertTrue(
        "Level of logger should be changed",
        (setInfo && org.apache.log4j.Level.INFO == logger.getLevel())
            || (!setInfo && Level.DEBUG == logger.getLevel()));
  }
}
