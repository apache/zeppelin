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

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;


public class WebDriverManager {

  public final static Logger LOG = LoggerFactory.getLogger(WebDriverManager.class);

  private static String downLoadsDir = "";

  public static WebDriver getWebDriver() {
    WebDriver driver = null;

    if (driver == null) {
      try {
        FirefoxBinary ffox = new FirefoxBinary();
        if ("true".equals(System.getenv("TRAVIS"))) {
          ffox.setEnvironmentProperty("DISPLAY", ":99"); // xvfb is supposed to
          // run with DISPLAY 99
        }
        int firefoxVersion = WebDriverManager.getFirefoxVersion();
        LOG.info("Firefox version " + firefoxVersion + " detected");

        downLoadsDir = FileUtils.getTempDirectory().toString();

        String tempPath = downLoadsDir + "/firebug/";

        downloadFireBug(firefoxVersion, tempPath);

        final String firebugPath = tempPath + "firebug.xpi";
        final String firepathPath = tempPath + "firepath.xpi";

        FirefoxProfile profile = new FirefoxProfile();
        profile.setPreference("browser.download.folderList", 2);
        profile.setPreference("browser.download.dir", downLoadsDir);
        profile.setPreference("browser.helperApps.alwaysAsk.force", false);
        profile.setPreference("browser.download.manager.showWhenStarting", false);
        profile.setPreference("browser.download.manager.showAlertOnComplete", false);
        profile.setPreference("browser.download.manager.closeWhenDone", true);
        profile.setPreference("app.update.auto", false);
        profile.setPreference("app.update.enabled", false);
        profile.setPreference("dom.max_script_run_time", 0);
        profile.setPreference("dom.max_chrome_script_run_time", 0);
        profile.setPreference("browser.helperApps.neverAsk.saveToDisk", "application/x-ustar,application/octet-stream,application/zip,text/csv,text/plain");
        profile.setPreference("network.proxy.type", 0);

        profile.addExtension(new File(firebugPath));
        profile.addExtension(new File(firepathPath));

        driver = new FirefoxDriver(ffox, profile);
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while FireFox Driver ", e);
      }
    }

    if (driver == null) {
      try {
        driver = new ChromeDriver();
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while ChromeDriver ", e);
      }
    }

    if (driver == null) {
      try {
        driver = new SafariDriver();
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while SafariDriver ", e);
      }
    }

    String url;
    if (System.getenv("url") != null) {
      url = System.getenv("url");
    } else {
      url = "http://localhost:8080";
    }

    long start = System.currentTimeMillis();
    boolean loaded = false;
    driver.manage().timeouts().implicitlyWait(AbstractZeppelinIT.MAX_IMPLICIT_WAIT,
        TimeUnit.SECONDS);
    driver.get(url);

    while (System.currentTimeMillis() - start < 60 * 1000) {
      // wait for page load
      try {
        (new WebDriverWait(driver, 5)).until(new ExpectedCondition<Boolean>() {
          @Override
          public Boolean apply(WebDriver d) {
            return d.findElement(By.xpath("//i[@tooltip='WebSocket Connected']"))
                .isDisplayed();
          }
        });
        loaded = true;
        break;
      } catch (TimeoutException e) {
        LOG.info("Exception in WebDriverManager while WebDriverWait ", e);
        driver.navigate().to(url);
      }
    }

    if (loaded == false) {
      fail();
    }

    return driver;
  }

  private static void downloadFireBug(int firefoxVersion, String tempPath) {
    String firebugUrlString = null;
    if (firefoxVersion < 23)
      firebugUrlString = "http://getfirebug.com/releases/firebug/1.11/firebug-1.11.4.xpi";
    else if (firefoxVersion >= 23 && firefoxVersion < 30)
      firebugUrlString = "http://getfirebug.com/releases/firebug/1.12/firebug-1.12.8.xpi";
    else if (firefoxVersion >= 30 && firefoxVersion < 33)
      firebugUrlString = "http://getfirebug.com/releases/firebug/2.0/firebug-2.0.7.xpi";
    else if (firefoxVersion >= 33)
      firebugUrlString = "http://getfirebug.com/releases/firebug/2.0/firebug-2.0.17.xpi";


    LOG.info("firebug version: " + firefoxVersion + ", will be downloaded to " + tempPath);
    try {
      File firebugFile = new File(tempPath + "firebug.xpi");
      URL firebugUrl = new URL(firebugUrlString);
      if (!firebugFile.exists()) {
        FileUtils.copyURLToFile(firebugUrl, firebugFile);
      }


      File firepathFile = new File(tempPath + "firepath.xpi");
      URL firepathUrl = new URL("https://addons.cdn.mozilla.net/user-media/addons/11900/firepath-0.9.7.1-fx.xpi");
      if (!firepathFile.exists()) {
        FileUtils.copyURLToFile(firepathUrl, firepathFile);
      }

    } catch (IOException e) {
      LOG.error("Download of firebug version: " + firefoxVersion + ", falied in path " + tempPath);
    }
    LOG.info("Download of firebug version: " + firefoxVersion + ", successful");
  }

  public static int getFirefoxVersion() {
    try {
      String firefoxVersionCmd = "firefox -v";
      if (System.getProperty("os.name").startsWith("Mac OS")) {
        firefoxVersionCmd = "/Applications/Firefox.app/Contents/MacOS/" + firefoxVersionCmd;
      }
      String versionString = (String) CommandExecutor.executeCommandLocalHost(firefoxVersionCmd, false, ProcessData.Types_Of_Data.OUTPUT);
      return Integer.valueOf(versionString.replaceAll("Mozilla Firefox", "").trim().substring(0, 2));
    } catch (Exception e) {
      LOG.error("Exception in WebDriverManager while getWebDriver ", e);
      return -1;
    }
  }
}
