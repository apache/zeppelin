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


import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public void testCreateNewButton() throws Exception {
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
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]")).click();
      waitForParagraph(2, "READY");
      Integer newNosOfParas = driver.findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("After Insert New (using Insert New button) :  number of  paragraph",
          oldNosOfParas + 1,
          CoreMatchers.equalTo(newNosOfParas));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='removeParagraph(paragraph)']")).click();
      ZeppelinITUtils.sleep(1000, false);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this paragraph')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(1000, false);

      setTextOfParagraph(1, " original paragraph ");

      WebElement newPara = driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class,'new-paragraph')][1]"));
      action.moveToElement(newPara).click().build().perform();
      ZeppelinITUtils.sleep(1000, false);
      waitForParagraph(1, "READY");

      collector.checkThat("Paragraph is created above",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo(StringUtils.EMPTY));
      setTextOfParagraph(1, " this is above ");

      newPara = driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class,'new-paragraph')][2]"));
      action.moveToElement(newPara).click().build().perform();

      waitForParagraph(3, "READY");

      collector.checkThat("Paragraph is created below",
          driver.findElement(By.xpath(getParagraphXPath(3) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo(StringUtils.EMPTY));
      setTextOfParagraph(3, " this is below ");

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

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testCreateNewButton ", e);
    }

  }

  @Test
  public void testRemoveButton() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
          .click();
      waitForParagraph(2, "READY");
      Integer oldNosOfParas = driver.findElements(By.xpath
          ("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("Before Remove : Number of paragraphs are ",
          oldNosOfParas,
          CoreMatchers.equalTo(2));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();

      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='removeParagraph(paragraph)']"));

      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this paragraph')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));

      Integer newNosOfParas = driver.findElements(By.xpath
          ("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      collector.checkThat("After Remove : Number of paragraphs are",
          newNosOfParas,
          CoreMatchers.equalTo(oldNosOfParas - 1));
      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testRemoveButton ", e);
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
      setTextOfParagraph(1, "1");

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]")).click();


      waitForParagraph(2, "READY");
      setTextOfParagraph(2, "2");


      collector.checkThat("The paragraph1 value contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo("1"));
      collector.checkThat("The paragraph1 value contains",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo("2"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='moveDown(paragraph)']"));

      collector.checkThat("The paragraph1 value contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo("2"));
      collector.checkThat("The paragraph1 value contains",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo("1"));

      driver.findElement(By.xpath(getParagraphXPath(2) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(2) + "//ul/li/a[@ng-click='moveUp(paragraph)']"));

      collector.checkThat("The paragraph1 value contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo("1"));
      collector.checkThat("The paragraph1 value contains",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]")).getText(),
          CoreMatchers.equalTo("2"));
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
      setTextOfParagraph(1, "println (\"abcd\")");

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='toggleEnableDisable(paragraph)']"));
      collector.checkThat("The play button class was ",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-control-play shortcut-icon']")).isDisplayed(), CoreMatchers.equalTo(false)
      );

      driver.findElement(By.xpath(".//*[@id='main']//button[contains(@ng-click, 'runAllParagraphs')]")).sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(2000, false);

      collector.checkThat("Paragraph status is ",
          getParagraphStatus(1), CoreMatchers.equalTo("READY")
      );

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testDisableParagraphRunButton ", e);
    }

  }

  @Test
  public void testClearOutputButton() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      String xpathToOutputField = getParagraphXPath(1) + "//div[contains(@id,\"_text\")]";
      setTextOfParagraph(1, "println (\"abcd\")");
      collector.checkThat("Before Run Output field contains ",
          driver.findElements(By.xpath(xpathToOutputField)).size(),
          CoreMatchers.equalTo(0));
      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("After Run Output field contains  ",
          driver.findElement(By.xpath(xpathToOutputField)).getText(),
          CoreMatchers.equalTo("abcd"));
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) +
          "//ul/li/a[@ng-click='clearParagraphOutput(paragraph)']"));
      collector.checkThat("After Clear  Output field contains ",
          driver.findElements(By.xpath(xpathToOutputField)).size(),
          CoreMatchers.equalTo(0));
      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testClearOutputButton ", e);
    }
  }

  @Test
  public void testWidth() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();
      waitForParagraph(1, "READY");

      collector.checkThat("Default Width is 12 ",
          driver.findElement(By.xpath("//div[contains(@class,'col-md-12')]")).isDisplayed(),
          CoreMatchers.equalTo(true));
      for (Integer newWidth = 1; newWidth <= 11; newWidth++) {
        clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
        String visibleText = newWidth.toString();
        new Select(driver.findElement(By.xpath(getParagraphXPath(1)
            + "//ul/li/a/form/select[(@ng-model='paragraph.config.colWidth')]"))).selectByVisibleText(visibleText);
        collector.checkThat("New Width is : " + newWidth,
            driver.findElement(By.xpath("//div[contains(@class,'col-md-" + newWidth + "')]")).isDisplayed(),
            CoreMatchers.equalTo(true));
      }
      deleteTestNotebook(driver);
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testWidth ", e);
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

      String xpathToTitle = getParagraphXPath(1) + "//div[contains(@class, 'title')]/div";
      String xpathToSettingIcon = getParagraphXPath(1) + "//span[@class='icon-settings']";
      String xpathToShowTitle = getParagraphXPath(1) + "//ul/li/a[@ng-show='!paragraph.config.title']";
      String xpathToHideTitle = getParagraphXPath(1) + "//ul/li/a[@ng-show='paragraph.config.title']";

      ZeppelinITUtils.turnOffImplicitWaits(driver);
      Integer titleElems = driver.findElements(By.xpath(xpathToTitle)).size();
      collector.checkThat("Before Show Title : The title doesn't exist",
          titleElems,
          CoreMatchers.equalTo(0));
      ZeppelinITUtils.turnOnImplicitWaits(driver);

      clickAndWait(By.xpath(xpathToSettingIcon));
      collector.checkThat("Before Show Title : The title option in option panel of paragraph is labeled as",
          driver.findElement(By.xpath(xpathToShowTitle)).getText(),
          CoreMatchers.allOf(CoreMatchers.startsWith("Show title"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+t")));

      clickAndWait(By.xpath(xpathToShowTitle));
      collector.checkThat("After Show Title : The title field contains",
          driver.findElement(By.xpath(xpathToTitle)).getText(),
          CoreMatchers.equalTo("Untitled"));

      clickAndWait(By.xpath(xpathToSettingIcon));
      collector.checkThat("After Show Title : The title option in option panel of paragraph is labeled as",
          driver.findElement(By.xpath(xpathToHideTitle)).getText(),
          CoreMatchers.allOf(CoreMatchers.startsWith("Hide title"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+t")));

      clickAndWait(By.xpath(xpathToHideTitle));
      ZeppelinITUtils.turnOffImplicitWaits(driver);
      titleElems = driver.findElements(By.xpath(xpathToTitle)).size();
      collector.checkThat("After Hide Title : The title field is hidden",
          titleElems,
          CoreMatchers.equalTo(0));
      ZeppelinITUtils.turnOnImplicitWaits(driver);

      driver.findElement(By.xpath(xpathToSettingIcon)).click();
      driver.findElement(By.xpath(xpathToShowTitle)).click();

      driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'title')]")).click();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//input")).sendKeys("NEW TITLE" + Keys.ENTER);
      ZeppelinITUtils.sleep(500, false);
      collector.checkThat("After Editing the Title : The title field contains ",
          driver.findElement(By.xpath(xpathToTitle)).getText(),
          CoreMatchers.equalTo("NEW TITLE"));
      driver.navigate().refresh();
      ZeppelinITUtils.sleep(1000, false);
      collector.checkThat("After Page Refresh : The title field contains ",
          driver.findElement(By.xpath(xpathToTitle)).getText(),
          CoreMatchers.equalTo("NEW TITLE"));
      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testTitleButton  ", e);
    }

  }

  @Test
  public void testShowAndHideLineNumbers() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      String xpathToLineNumberField = getParagraphXPath(1) + "//div[contains(@class, 'ace_gutter-layer')]";
      String xpathToShowLineNumberButton = getParagraphXPath(1) + "//ul/li/a[@ng-click='showLineNumbers(paragraph)']";
      String xpathToHideLineNumberButton = getParagraphXPath(1) + "//ul/li/a[@ng-click='hideLineNumbers(paragraph)']";

      collector.checkThat("Before \"Show line number\" the Line Number is Enabled ",
          driver.findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
          CoreMatchers.equalTo(false));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      collector.checkThat("Before \"Show line number\" The option panel in paragraph has button labeled ",
          driver.findElement(By.xpath(xpathToShowLineNumberButton)).getText(),
          CoreMatchers.allOf(CoreMatchers.startsWith("Show line numbers"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+m")));


      clickAndWait(By.xpath(xpathToShowLineNumberButton));
      collector.checkThat("After \"Show line number\" the Line Number is Enabled ",
          driver.findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
          CoreMatchers.equalTo(true));

      clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
      collector.checkThat("After \"Show line number\" The option panel in paragraph has button labeled ",
          driver.findElement(By.xpath(xpathToHideLineNumberButton)).getText(),
          CoreMatchers.allOf(CoreMatchers.startsWith("Hide line numbers"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+m")));

      clickAndWait(By.xpath(xpathToHideLineNumberButton));
      collector.checkThat("After \"Hide line number\" the Line Number is Enabled",
          driver.findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
          CoreMatchers.equalTo(false));
      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testShowAndHideLineNumbers ", e);
    }
  }

  @Test
  public void testEditOnDoubleClick() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();
      Actions action = new Actions(driver);

      waitForParagraph(1, "READY");

      setTextOfParagraph(1, "%md");
      driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(Keys.ARROW_RIGHT);
      driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(Keys.ENTER);
      driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(Keys.SHIFT + "3");
      driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(" abc");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      collector.checkThat("Markdown editor is hidden after run ",
          driver.findElements(By.xpath(getParagraphXPath(1) + "//div[contains(@ng-if, 'paragraph.config.editorHide')]")).size(),
          CoreMatchers.equalTo(0));

      collector.checkThat("Markdown editor is shown after run ",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@ng-show, 'paragraph.config.tableHide')]")).isDisplayed(),
          CoreMatchers.equalTo(true));

      // to check if editOnDblClick field is fetched correctly after refresh
      driver.navigate().refresh();
      waitForParagraph(1, "FINISHED");

      action.doubleClick(driver.findElement(By.xpath(getParagraphXPath(1)))).perform();
      ZeppelinITUtils.sleep(1000, false);
      collector.checkThat("Markdown editor is shown after double click ",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@ng-if, 'paragraph.config.editorHide')]")).isDisplayed(),
          CoreMatchers.equalTo(true));

      collector.checkThat("Markdown editor is hidden after double click ",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@ng-show, 'paragraph.config.tableHide')]")).isDisplayed(),
          CoreMatchers.equalTo(false));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testEditOnDoubleClick ", e);
    }
  }
}
