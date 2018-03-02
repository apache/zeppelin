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


import java.util.concurrent.TimeUnit;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public static void performDoubleClick(WebDriver driver, WebElement element) {
    //This will be fixed in FireFox-59 which is not yet released.
    //https://bugzilla.mozilla.org/show_bug.cgi?id=1385476
    //Actions action = new Actions(driver);
    //action.doubleClick(element).perform();

    ((JavascriptExecutor) driver).executeScript("return (function(target) {\n"
        + "if (target.fireEvent) {\n"
        + "target.fireEvent('ondblclick');\n"
        + "} else {\n"
        + "var evObj = new MouseEvent('dblclick', {\n"
        + "bubbles: true,\n"
        + "cancelable: true,\n"
        + "view: window\n"
        + "});\n"
        + "target.dispatchEvent(evObj);\n"
        + "}\n"
        + "return true;\n"
        + "})(arguments[0]);", element);
  }

  public static boolean isRunning() {
    String statusString = (String) CommandExecutor
        .executeCommandLocalHost("../bin/zeppelin-daemon.sh restart", false,
            ProcessData.Types_Of_Data.OUTPUT);
    // TODO(prabhjyotsingh) remove this block, this is only for testing
    if (statusString.contains("not")) {
      LOG.error("Zeppelin server was not running!!!");
    }
    return !statusString.contains("not");
  }

  public static void restartZeppelin() {
    CommandExecutor.executeCommandLocalHost("../bin/zeppelin-daemon.sh restart",
        false, ProcessData.Types_Of_Data.OUTPUT);
    //wait for server to start.
    sleep(5000, false);
  }

  public static void turnOffImplicitWaits(WebDriver driver) {
    driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
  }

  public static void turnOnImplicitWaits(WebDriver driver) {
    driver.manage().timeouts().implicitlyWait(AbstractZeppelinIT.MAX_IMPLICIT_WAIT,
        TimeUnit.SECONDS);
  }
}
