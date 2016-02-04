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

package org.apache.zeppelin.integration;


import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ParagraphActionsIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(ParagraphActionsIT.class);


  @Rule
  public ErrorCollector collector = new ErrorCollector();

  @Before
  public void startUp() {
    if (!endToEndTestEnabled()) {
      return;
    }
    driver = WebDriverManager.getWebDriver();
  }

  @After
  public void tearDown() {
    if (!endToEndTestEnabled()) {
      return;
    }

    driver.quit();
  }

  @Test
  public void testTitleButton() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");

      collector.checkThat("Before Show Title : The title field contains",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'binding')]")).getText(),
              CoreMatchers.equalTo(""));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      collector.checkThat("Before Show Title : The title option in option panel of paragraph is labeled as  ",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//a[contains(@ng-click, 'showTitle()')]")).getText(),
              CoreMatchers.equalTo("Show title"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='showTitle()']")).click();
      collector.checkThat("After Show Title : The title field contains",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'binding')]")).getText(),
              CoreMatchers.equalTo("Untitled"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      collector.checkThat("After Show Title : The title option in option panel of paragraph is labeled as",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//a[contains(@ng-click, 'hideTitle()')]")).getText(),
              CoreMatchers.equalTo("Hide title"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='hideTitle()']")).click();
      collector.checkThat("After Hide Title : The title field contains",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'binding')]")).getText(),
              CoreMatchers.equalTo(""));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='showTitle()']")).click();

      driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'title')]")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//input")).sendKeys("NEW TITLE" + Keys.ENTER);
      collector.checkThat("After Editing the Title : The title field contains ",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'binding')]")).getText(),
              CoreMatchers.equalTo("NEW TITLE"));
      ZeppelinITUtils.sleep(1000,false);
      driver.navigate().refresh();
      collector.checkThat("After Page Refresh : The title field contains ",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'binding')]")).getText(),
              CoreMatchers.equalTo("NEW TITLE"));
      deleteTestNotebook(driver);

    } catch (ElementNotVisibleException e) {
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    }

  }

  @Test
  public void testDisableParagraphRunButton() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));
      paragraph1Editor.sendKeys("println" + Keys.chord(Keys.SHIFT, "9") + "\""
          + "abcd\")");

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='toggleEnableDisable()']")).click();
      collector.checkThat("The play button class was ",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-control-play']")).isDisplayed(), CoreMatchers.equalTo(false)
      );

      driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='runNote()']")).sendKeys(Keys.ENTER);
      sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      sleep(2000, false);

      collector.checkThat("Paragraph status is ",
          getParagraphStatus(1), CoreMatchers.equalTo("READY")
      );


      deleteTestNotebook(driver);

    } catch (ElementNotVisibleException e) {
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    }

  }
}