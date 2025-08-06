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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class auto discovery the available WebDriver in the following priority:
//   Chrome, Firefox, Safari.
//
// You can also use the environment variable ZEPPELIN_SELENIUM_BROWSER to choose a specific one.
// For example, unlike Chromium and Firefox drivers, Safari's WebDriver is pre-installed on macOS,
// to enable automation on Safari, just simply run the following command from the terminal:
//   safaridriver --enable
// and then specify Safari for Zeppelin Selenium integration tests:
//   export ZEPPELIN_SELENIUM_BROWSER=Safari
//
// To learn more about WebDriver, visit: https://www.selenium.dev/documentation/webdriver/
public class WebDriverManager implements Closeable {

  public final static Logger LOG = LoggerFactory.getLogger(WebDriverManager.class);

  final boolean deleteTempFiles;
  final Path logDir;
  final Path downloadDir;
  final WebDriver driver;

  public WebDriverManager(boolean deleteTempFiles, int port) throws IOException {
    this.deleteTempFiles = deleteTempFiles;
    this.downloadDir = Files.createTempFile("browser", ".download");
    this.logDir = Files.createTempFile("logdir", ".download");
    this.driver = constructWebDriver(port);
  }

  public WebDriverManager(int port) throws IOException {
    this(true, port);
  }

  public WebDriver getWebDriver() {
    return this.driver;
  }

  private WebDriver constructWebDriver(int port) {
    Supplier<WebDriver> chromeDriverSupplier = () -> {
      try {
        ChromeOptions options = new ChromeOptions();
        return new ChromeDriver(options);
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while ChromeDriver ", e);
        return null;
      }
    };
    Supplier<WebDriver> firefoxDriverSupplier = () -> {
      try {
        return getFirefoxDriver();
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while FireFox Driver ", e);
        return null;
      }
    };
    Supplier<WebDriver> safariDriverSupplier = () -> {
      try {
        return new SafariDriver();
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while SafariDriver ", e);
        return null;
      }
    };

    WebDriver driver;
    switch (SystemUtils.getEnvironmentVariable("ZEPPELIN_SELENIUM_BROWSER", "").toLowerCase(Locale.ROOT)) {
      case "chrome":
        driver = chromeDriverSupplier.get();
        break;
      case "firefox":
        driver = firefoxDriverSupplier.get();
        break;
      case "safari":
        driver = safariDriverSupplier.get();
        break;
      default:
        driver = Stream.of(chromeDriverSupplier, firefoxDriverSupplier, safariDriverSupplier)
            .map(Supplier::get)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
    if (driver == null) {
      throw new RuntimeException("No available WebDriver");
    }

    String url = "http://localhost:" + port + "/classic";

    long start = System.currentTimeMillis();
    boolean loaded = false;
    driver.manage().timeouts()
      .implicitlyWait(Duration.ofSeconds(AbstractZeppelinIT.MAX_IMPLICIT_WAIT));
    driver.get(url);

    while (System.currentTimeMillis() - start < 60 * 1000) {
      // wait for page load
      try {
        (new WebDriverWait(driver, Duration.ofSeconds(60))).until(new ExpectedCondition<Boolean>() {
          @Override
          public Boolean apply(WebDriver d) {
            return d.findElement(By.xpath("//i[@uib-tooltip='WebSocket Connected']"))
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

    assertTrue(loaded);

    try {
      driver.manage().window().maximize();
    } catch (Exception e) {
      LOG.warn("Failed to maximize browser window. Consider using setSize() instead.", e);
    }

    return driver;
  }

  private WebDriver getFirefoxDriver() {

    FirefoxProfile profile = new FirefoxProfile();
    profile.setPreference("browser.download.folderList", 2);
    profile.setPreference("browser.download.dir", downloadDir.toString());
    profile.setPreference("browser.helperApps.alwaysAsk.force", false);
    profile.setPreference("browser.download.manager.showWhenStarting", false);
    profile.setPreference("browser.download.manager.showAlertOnComplete", false);
    profile.setPreference("browser.download.manager.closeWhenDone", true);
    profile.setPreference("app.update.auto", false);
    profile.setPreference("app.update.enabled", false);
    profile.setPreference("dom.max_script_run_time", 0);
    profile.setPreference("dom.max_chrome_script_run_time", 0);
    profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
      "application/x-ustar,application/octet-stream,application/zip,text/csv,text/plain");
    profile.setPreference("network.proxy.type", 0);

    FirefoxOptions firefoxOptions = new FirefoxOptions();
    firefoxOptions.setProfile(profile);

    LOG.info("Firefox version " + firefoxOptions.getBrowserVersion() + " detected");
    GeckoDriverService service =
      new GeckoDriverService.Builder().withLogFile(logDir.toFile()).build();
    // System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE, "true");

    return new FirefoxDriver(service, firefoxOptions);
  }

  @Override
  public void close() throws IOException {
    driver.close();
    if (deleteTempFiles) {
      Files.delete(downloadDir);
      Files.delete(logDir);
    }
  }
}
