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


import com.google.common.base.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.WebDriver;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.apache.zeppelin.AbstractZeppelinIT.MAX_BROWSER_TIMEOUT_SEC;

public class ZeppelinITUtils {

  private final static Logger LOG = LoggerFactory.getLogger(ZeppelinITUtils.class);

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
    //wait for server to start.
    sleep(5000, false);
  }

  public static void turnOffImplicitWaits(WebDriver driver) {
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
  }

  public static void turnOnImplicitWaits(WebDriver driver) {
    driver.manage().timeouts()
      .implicitlyWait(Duration.ofSeconds(AbstractZeppelinIT.MAX_IMPLICIT_WAIT));
  }
}
