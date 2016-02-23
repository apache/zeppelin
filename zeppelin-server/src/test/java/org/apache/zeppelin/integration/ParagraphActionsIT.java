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
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
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
  public void testCreateNewButton() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();
      Actions action = new Actions(driver);
      waitForParagraph(1, "READY");
      Integer oldNosOfParas = driver.findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("Before Insert New : the number of  paragraph ",
        oldNosOfParas,
        CoreMatchers.equalTo(1));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='insertNew()']")).click();
      waitForParagraph(2, "READY");
      Integer newNosOfParas = driver.findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("After Insert New (using Insert New button) :  number of  paragraph",
        oldNosOfParas + 1,
        CoreMatchers.equalTo(newNosOfParas));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='removeParagraph()']")).click();
      ZeppelinITUtils.sleep(1000, false);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this paragraph')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(1000, false);

      WebElement oldParagraphEditor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));
      oldParagraphEditor.sendKeys(" original paragraph ");

      WebElement newPara = driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class,'new-paragraph')][1]"));
      action.moveToElement(newPara).click().build().perform();
      ZeppelinITUtils.sleep(1000, false);
      waitForParagraph(1, "READY");

      collector.checkThat("Paragraph is created above",
        driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
        CoreMatchers.equalTo(""));
      WebElement aboveParagraphEditor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));
      aboveParagraphEditor.sendKeys(" this is above ");

      newPara = driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class,'new-paragraph')][2]"));
      action.moveToElement(newPara).click().build().perform();

      waitForParagraph(3, "READY");

      collector.checkThat("Paragraph is created below",
        driver.findElement(By.xpath(getParagraphXPath(3) + "//div[contains(@class, 'editor')]")).getText(),
        CoreMatchers.equalTo(""));
      WebElement belowParagraphEditor = driver.findElement(By.xpath(getParagraphXPath(3) + "//textarea"));
      belowParagraphEditor.sendKeys(" this is below ");

      collector.checkThat("The output field of paragraph1 contains",
        driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
        CoreMatchers.equalTo(" this is above "));
      collector.checkThat("The output field paragraph2 contains",
        driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
        CoreMatchers.equalTo(" original paragraph "));
      collector.checkThat("The output field paragraph3 contains",
        driver.findElement(By.xpath(getParagraphXPath(3) + "//div[contains(@class, 'editor')]")).getText(),
        CoreMatchers.equalTo(" this is below "));
      collector.checkThat("The current number of paragraphs after creating  paragraph above and below",
        driver.findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size(),
        CoreMatchers.equalTo(3));

      ZeppelinITUtils.sleep(1000, false);
      deleteTestNotebook(driver);

    } catch (ElementNotVisibleException e) {
      LOG.error("Exception in ParagraphActionsIT while testCreateNewButton ", e);
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      throw e;

    }


  }

  @Test
  public void testRemoveButton() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='insertNew()']")).click();
      waitForParagraph(2, "READY");
      Integer oldNosOfParas = driver.findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("Before Remove : Number of paragraphs are ",
        oldNosOfParas,
        CoreMatchers.equalTo(2));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='removeParagraph()']")).click();
      sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this paragraph')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      Integer newNosOfParas = driver.findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("After Remove : Number of paragraphs are",
        oldNosOfParas-1,
        CoreMatchers.equalTo(newNosOfParas));
      ZeppelinITUtils.sleep(1000, false);
      deleteTestNotebook(driver);

    } catch (Exception e) {
      LOG.error("Exception in ParagraphActionsIT while testRemoveButton ", e);
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
      handleException("Exception in ParagraphActionsIT while testMoveUpAndDown ", e);
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
      handleException("Exception in ParagraphActionsIT while testDisableParagraphRunButton ", e);
    }

  }

  @Test
  public void testTitleButton() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      ZeppelinITUtils.sleep(1000, false);

      String xpathToTile = getParagraphXPath(1) + "//div[contains(@class, 'title')]/div";
      String xpathToSettingIcon = getParagraphXPath(1) + "//span[@class='icon-settings']";

      collector.checkThat("Before Show Title : The title field contains",
          driver.findElement(By.xpath(xpathToTile)).getText(),
          CoreMatchers.equalTo(""));
      driver.findElement(By.xpath(xpathToSettingIcon)).click();
      collector.checkThat("Before Show Title : The title option in option panel of paragraph is labeled as  ",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//a[contains(@ng-click, 'showTitle()')]")).getText(),
          CoreMatchers.equalTo("Show title"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='showTitle()']")).click();
      collector.checkThat("After Show Title : The title field contains",
          driver.findElement(By.xpath(xpathToTile)).getText(),
          CoreMatchers.equalTo("Untitled"));

      driver.findElement(By.xpath(xpathToSettingIcon)).click();
      collector.checkThat("After Show Title : The title option in option panel of paragraph is labeled as",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//a[contains(@ng-click, 'hideTitle()')]")).getText(),
          CoreMatchers.equalTo("Hide title"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='hideTitle()']")).click();
      collector.checkThat("After Hide Title : The title field contains",
          driver.findElement(By.xpath(xpathToTile)).getText(),
          CoreMatchers.equalTo(""));
      driver.findElement(By.xpath(xpathToSettingIcon)).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='showTitle()']")).click();

      driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'title')]")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//input")).sendKeys("NEW TITLE" + Keys.ENTER);
      collector.checkThat("After Editing the Title : The title field contains ",
          driver.findElement(By.xpath(xpathToTile)).getText(),
          CoreMatchers.equalTo("NEW TITLE"));
      driver.navigate().refresh();
      ZeppelinITUtils.sleep(1000, false);
      collector.checkThat("After Page Refresh : The title field contains ",
          driver.findElement(By.xpath(xpathToTile)).getText(),
          CoreMatchers.equalTo("NEW TITLE"));
      ZeppelinITUtils.sleep(1000, false);
      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testTitleButton  ", e);
    }

  }
}