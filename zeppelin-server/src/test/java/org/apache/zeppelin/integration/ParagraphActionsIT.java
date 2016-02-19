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
  public void testShowAndHideLineNumbers() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      collector.checkThat("Before \"Show line number\" the Line Number is Enabled ",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'ace_gutter-layer')]")).isDisplayed(),
              CoreMatchers.equalTo(false));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      collector.checkThat("Before \"Show line number\" The option panel in paragraph has button labeled ",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//a[contains(@ng-click, 'showLineNumbers()')]")).getText(),
              CoreMatchers.equalTo("Show line numbers"));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='showLineNumbers()']")).click();
      collector.checkThat("After \"Show line number\" the Line Number is Enabled " ,
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'ace_gutter-layer')]")).isDisplayed(),
              CoreMatchers.equalTo(true));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      collector.checkThat("After \"Show line number\" The option panel in paragraph has button labeled ",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//a[contains(@ng-click, 'hideLineNumbers()')]")).getText(),
              CoreMatchers.equalTo("Hide line numbers"));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='hideLineNumbers()']")).click();
      collector.checkThat("After \"Hide line number\" the Line Number is Enabled",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'ace_gutter-layer')]")).isDisplayed(),
              CoreMatchers.equalTo(false));
      ZeppelinITUtils.sleep(1000, false);
      deleteTestNotebook(driver);

    } catch (ElementNotVisibleException e) {
       LOG.error("Exception in ParagraphActionsIT while testShowAndHideLineNumbers ", e);
       File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
       throw e;
    }


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

    } catch (Exception e) {
      LOG.error("Exception in ParagraphActionsIT while testTitleButton ", e);
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      throw e;
    }

  }


  @Test
  public void testMoveUpAndDown() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));
      paragraph1Editor.sendKeys("1");

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='insertNew()']")).click();


      waitForParagraph(2, "READY");
      WebElement paragraph2Editor = driver.findElement(By.xpath(getParagraphXPath(2) + "//textarea"));
      paragraph2Editor.sendKeys("2");


      collector.checkThat("The paragraph1 value contains",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
              CoreMatchers.equalTo("1"));
      collector.checkThat("The paragraph1 value contains",
              driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
              CoreMatchers.equalTo("2"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='moveDown()']")).click();

      ZeppelinITUtils.sleep(1000,false);

      collector.checkThat("The paragraph1 value contains",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
              CoreMatchers.equalTo("2"));
      collector.checkThat("The paragraph1 value contains",
              driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
              CoreMatchers.equalTo("1"));

      driver.findElement(By.xpath(getParagraphXPath(2) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(2) + "//ul/li/a[@ng-click='moveUp()']")).click();

      ZeppelinITUtils.sleep(1000,false);

      collector.checkThat("The paragraph1 value contains",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
              CoreMatchers.equalTo("1"));
      collector.checkThat("The paragraph1 value contains",
              driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
              CoreMatchers.equalTo("2"));
      ZeppelinITUtils.sleep(1000,false);
      deleteTestNotebook(driver);

    } catch (Exception e) {
      LOG.error("Exception in ParagraphActionsIT while testMoveUpAndDown ", e);
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      throw e;
    }

  }

  @Test
  public void testDisableParagraphRunButton() throws Exception {
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

    } catch (Exception e) {
      LOG.error("Exception in ParagraphActionsIT while testDisableParagraphRunButton ", e);
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      throw e;
    }

  }
}
