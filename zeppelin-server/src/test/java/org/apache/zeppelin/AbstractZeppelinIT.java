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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.openqa.selenium.Keys.ENTER;
import static org.openqa.selenium.Keys.SHIFT;

abstract public class AbstractZeppelinIT {
  protected WebDriver driver;

  protected final static Logger LOG = LoggerFactory.getLogger(AbstractZeppelinIT.class);
  protected static final long MAX_IMPLICIT_WAIT = 30;
  protected static final long MAX_BROWSER_TIMEOUT_SEC = 30;
  protected static final long MAX_PARAGRAPH_TIMEOUT_SEC = 60;

  protected void sleep(long millis, boolean logOutput) {
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

  protected void setTextOfParagraph(int paragraphNo, String text) {
    String editorId = driver.findElement(By.xpath(getParagraphXPath(paragraphNo) + "//div[contains(@class, 'editor')]")).getAttribute("id");
    if (driver instanceof JavascriptExecutor) {
      ((JavascriptExecutor) driver).executeScript("ace.edit('" + editorId + "'). setValue('" + text + "')");
    } else {
      throw new IllegalStateException("This driver does not support JavaScript!");
    }
  }

  protected void runParagraph(int paragraphNo) {
    driver.findElement(By.xpath(getParagraphXPath(paragraphNo) + "//span[@class='icon-control-play']")).click();
  }


  protected String getParagraphXPath(int paragraphNo) {
    return "//div[@ng-controller=\"ParagraphCtrl\"][" + paragraphNo + "]";
  }

  protected boolean waitForParagraph(final int paragraphNo, final String state) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]//span[1][contains(.,'" + state + "')]");
    WebElement element = pollingWait(locator, MAX_PARAGRAPH_TIMEOUT_SEC);
    return element.isDisplayed();
  }

  protected String getParagraphStatus(final int paragraphNo) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]//span[1]");

    return driver.findElement(locator).getText();
  }

  protected boolean waitForText(final String txt, final By locator) {
    try {
      WebElement element = pollingWait(locator, MAX_BROWSER_TIMEOUT_SEC);
      return txt.equals(element.getText());
    } catch (TimeoutException e) {
      return false;
    }
  }

  protected WebElement pollingWait(final By locator, final long timeWait) {
    Wait<WebDriver> wait = new FluentWait<WebDriver>(driver)
        .withTimeout(timeWait, TimeUnit.SECONDS)
        .pollingEvery(1, TimeUnit.SECONDS)
        .ignoring(NoSuchElementException.class);

    return wait.until(new Function<WebDriver, WebElement>() {
      public WebElement apply(WebDriver driver) {
        return driver.findElement(locator);
      }
    });
  }

  protected boolean endToEndTestEnabled() {
    return null != System.getenv("TEST_SELENIUM");
  }

  protected void createNewNote() {
    List<WebElement> notebookLinks = driver.findElements(By
        .xpath("//div[contains(@class, \"col-md-4\")]/div/ul/li"));
    List<String> notebookTitles = new LinkedList<String>();
    for (WebElement el : notebookLinks) {
      notebookTitles.add(el.getText());
    }

    WebElement createNoteLink = driver.findElement(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new note')]"));
    createNoteLink.click();

    WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    WebElement modal = block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteNameModal")));
    WebElement createNoteButton = modal.findElement(By.id("createNoteButton"));
    createNoteButton.click();

    try {
      Thread.sleep(500); // wait for notebook list updated
    } catch (InterruptedException e) {
    }
  }

  protected void deleteTestNotebook(final WebDriver driver) {
    driver.findElement(By.xpath("//*[@id='main']/div//h3/span/button[@tooltip='Remove the notebook']"))
        .sendKeys(Keys.ENTER);
    sleep(1000, true);
    driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    sleep(100, true);
  }

  protected void handleException(String message, Exception e) throws Exception {
    LOG.error(message, e);
    File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    LOG.error("ScreenShot::\ndata:image/png;base64," + new String(Base64.encodeBase64(FileUtils.readFileToByteArray(scrFile))));
    throw e;
  }

}
