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
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestUtils {

  protected final static Logger LOG = LoggerFactory.getLogger(TestUtils.class);
  protected static final long MAX_BROWSER_TIMEOUT_SEC = 30;
  protected static final long MAX_PARAGRAPH_TIMEOUT_SEC = 60;

  public static void sleep(long millis, boolean logOutput) {
    if (logOutput) {
      LOG.info("Starting sleeping for " + (millis / 1000) + " seconds...");
      LOG.info("Caller: " + Thread.currentThread().getStackTrace()[2]);
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if (logOutput) {
      LOG.info("Finished.");
    }
  }


  public static String getParagraphXPath(int paragraphNo) {
    return "//div[@ng-controller=\"ParagraphCtrl\"][" + paragraphNo +"]";
  }

  public static boolean waitForParagraph(final int paragraphNo, final String state, final WebDriver driver) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
            + "//div[contains(@class, 'control')]//span[1][contains(.,'" + state + "')]");
    WebElement element = pollingWait(locator, TestUtils.MAX_PARAGRAPH_TIMEOUT_SEC, driver);
    return element.isDisplayed();
  }

  public static String getParagraphStatus(final int paragraphNo, final WebDriver driver) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
            + "//div[contains(@class, 'control')]//span[1]");

    return driver.findElement(locator).getText();
  }

  public static boolean waitForText(final String txt, final By locator, final WebDriver driver) {
    try {
      WebElement element = pollingWait(locator, TestUtils.MAX_BROWSER_TIMEOUT_SEC, driver);
      return txt.equals(element.getText());
    } catch (TimeoutException e) {
      return false;
    }
  }

  static public WebElement pollingWait(final By locator, final long timeWait, final WebDriver driver) {
    Wait<WebDriver> wait = new FluentWait<WebDriver>(driver)
            .withTimeout(timeWait, TimeUnit.SECONDS)
            .pollingEvery(1, TimeUnit.SECONDS)
            .ignoring(NoSuchElementException.class);

    return wait.until(new Function<WebDriver, WebElement>() {
      public WebElement apply(WebDriver driver) {
        return driver.findElement(locator);
      }
    });
  };

  public static boolean endToEndTestEnabled() {
    return null != System.getenv("CI");
  }

  public static void createNewNote(final WebDriver driver) {
    List<WebElement> notebookLinks = driver.findElements(By
            .xpath("//div[contains(@class, \"col-md-4\")]/div/ul/li"));
    List<String> notebookTitles = new LinkedList<String>();
    for (WebElement el : notebookLinks) {
      notebookTitles.add(el.getText());
    }

    WebElement createNoteLink = driver.findElement(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new note')]"));
    createNoteLink.click();

    WebDriverWait block = new WebDriverWait(driver, TestUtils.MAX_BROWSER_TIMEOUT_SEC);
    WebElement modal = block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteNameModal")));
    WebElement createNoteButton = modal.findElement(By.id("createNoteButton"));
    createNoteButton.click();

    try {
      Thread.sleep(500); // wait for notebook list updated
    } catch (InterruptedException e) {
    }
  }

  public static void deleteTestNotebook(final WebDriver driver) {
    driver.findElement(By.xpath("//*[@id='main']/div//h3/span[1]/button[@tooltip='Remove the notebook']"))
            .sendKeys(Keys.ENTER);
    TestUtils.sleep(1000, true);
    driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
            "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    TestUtils.sleep(100, true);
  }

}
