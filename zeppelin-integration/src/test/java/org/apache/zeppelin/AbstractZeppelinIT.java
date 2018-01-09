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
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.concurrent.TimeUnit;

abstract public class AbstractZeppelinIT {
  protected static WebDriver driver;

  protected final static Logger LOG = LoggerFactory.getLogger(AbstractZeppelinIT.class);
  protected static final long MIN_IMPLICIT_WAIT = 5;
  protected static final long MAX_IMPLICIT_WAIT = 30;
  protected static final long MAX_BROWSER_TIMEOUT_SEC = 30;
  protected static final long MAX_PARAGRAPH_TIMEOUT_SEC = 120;

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
    return "(//div[@ng-controller=\"ParagraphCtrl\"])[" + paragraphNo + "]";
  }

  protected String getNoteFormsXPath() {
    return "(//div[@id='noteForms'])";
  }

  protected boolean waitForParagraph(final int paragraphNo, final String state) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]//span[2][contains(.,'" + state + "')]");
    WebElement element = pollingWait(locator, MAX_PARAGRAPH_TIMEOUT_SEC);
    return element.isDisplayed();
  }

  protected String getParagraphStatus(final int paragraphNo) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]/span[2]");

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
    Wait<WebDriver> wait = new FluentWait<>(driver)
        .withTimeout(timeWait, TimeUnit.SECONDS)
        .pollingEvery(1, TimeUnit.SECONDS)
        .ignoring(NoSuchElementException.class);

    return wait.until(new Function<WebDriver, WebElement>() {
      public WebElement apply(WebDriver driver) {
        return driver.findElement(locator);
      }
    });
  }

  protected void createNewNote() {
    clickAndWait(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
        " note')]"));

    WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteCreateModal")));
    clickAndWait(By.id("createNoteButton"));
    block.until(ExpectedConditions.invisibilityOfElementLocated(By.className("pull-right")));
  }

  protected void deleteTestNotebook(final WebDriver driver) {
    WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']"))
        .sendKeys(Keys.ENTER);
    block.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']")));
    driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'This note will be moved to trash')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    ZeppelinITUtils.sleep(100, true);
  }

  protected void clickAndWait(final By locator) {
    pollingWait(locator, MAX_IMPLICIT_WAIT).click();
    ZeppelinITUtils.sleep(1000, true);
  }

  protected void handleException(String message, Exception e) throws Exception {
    LOG.error(message, e);
    File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    LOG.error("ScreenShot::\ndata:image/png;base64," + new String(Base64.encodeBase64(FileUtils.readFileToByteArray(scrFile))));
    throw e;
  }

}
