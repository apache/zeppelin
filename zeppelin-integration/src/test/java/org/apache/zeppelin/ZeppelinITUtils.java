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

package org.apache.zeppelin;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.WebDriver;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class ZeppelinITUtils {

  public final static Logger LOG = LoggerFactory.getLogger(ZeppelinITUtils.class);

  public static void sleep(long millis, boolean logOutput) {
    if (logOutput) {
      LOG.info("Starting sleeping for " + (millis / 1000) + " seconds...");
      LOG.info("Caller: " + Thread.currentThread().getStackTrace()[2]);
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      LOG.error("Exception in WebDriverManager while getWebDriver ", e);
    }
    if (logOutput) {
      LOG.info("Finished.");
    }
  }

  public static void restartZeppelin() {
    CommandExecutor.executeCommandLocalHost("../bin/zeppelin-daemon.sh restart",
        false, ProcessData.Types_Of_Data.OUTPUT);
    LOG.info("Waiting for server to start for 30 seconds...");
    long deadline = System.nanoTime() + 30000000000L;  // 30s
    while (System.nanoTime() < deadline) {
      try {
        URL url = new URL(WebDriverManager.getUrl());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try {
          connection.connect();
          if (connection.getResponseCode() == 200) {
            LOG.info("Server is ready.");
            return;
          }
        } finally {
          connection.disconnect();
        }
      } catch (Exception e) {
        // ignore, most likely server is not ready
      }
    }
    throw new RuntimeException("Zeppelin server did not start in 30s");
  }

  public static void turnOffImplicitWaits(WebDriver driver) {
    driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
  }

  public static void turnOnImplicitWaits(WebDriver driver) {
    driver.manage().timeouts().implicitlyWait(AbstractZeppelinIT.MAX_IMPLICIT_WAIT,
        TimeUnit.SECONDS);
  }
}
