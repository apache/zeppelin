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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

class ParagraphActionsIT extends AbstractZeppelinIT {

  @BeforeEach
  public void startUp() throws IOException {
    manager = new WebDriverManager();
  }

  @AfterEach
  public void tearDown() throws IOException {
    manager.close();
  }

  @Test
  void testCreateNewButton() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      Integer oldNosOfParas = manager.getWebDriver()
        .findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      assertEquals(1, oldNosOfParas, "Before Insert New : the number of  paragraph ");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
        .click();
      waitForParagraph(2, "READY");
      Integer newNosOfParas = manager.getWebDriver()
        .findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      assertEquals(newNosOfParas, oldNosOfParas + 1,
        "After Insert New (using Insert New button) :  number of  paragraph");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
        .findElement(
          By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='removeParagraph(paragraph)']"))
        .click();
      ZeppelinITUtils.sleep(1000, false);
      manager.getWebDriver()
        .findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this paragraph')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(1000, false);

      setTextOfParagraph(1, " original paragraph ");

      clickAndWait(By.xpath(getParagraphXPath(1) + "//div[contains(@class,'new-paragraph')][1]"));
      waitForParagraph(1, "READY");

      assertEquals(StringUtils.EMPTY,
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "Paragraph is created above");
      setTextOfParagraph(1, " this is above ");

      clickAndWait(By.xpath(getParagraphXPath(2) + "//div[contains(@class,'new-paragraph')][2]"));

      waitForParagraph(3, "READY");

      assertEquals(StringUtils.EMPTY,
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(3) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "Paragraph is created below");
      setTextOfParagraph(3, " this is below ");

      assertEquals(" this is above ",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The output field of paragraph1 contains");
      assertEquals(" original paragraph ",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The output field paragraph2 contains");
      assertEquals(" this is below ",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(3) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The output field paragraph3 contains");
      assertEquals(3,
        manager.getWebDriver().findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]"))
          .size(),
        "The current number of paragraphs after creating  paragraph above and below");

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testCreateNewButton ", e);
    }

  }

  @Test
  void testRemoveButton() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
          .click();
      waitForParagraph(2, "READY");
      Integer oldNosOfParas =
        manager.getWebDriver().findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]"))
          .size();
      assertEquals(2, oldNosOfParas, "Before Remove : Number of paragraphs are ");
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();

      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='removeParagraph(paragraph)']"));

      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this paragraph')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));

      Integer newNosOfParas = manager.getWebDriver()
        .findElements(By.xpath("//div[@ng-controller=\"ParagraphCtrl\"]")).size();
      assertEquals(oldNosOfParas - 1, newNosOfParas, "After Remove : Number of paragraphs are");
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testRemoveButton ", e);
    }
  }

  @Test
  void testMoveUpAndDown() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      setTextOfParagraph(1, "1");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
        .click();


      waitForParagraph(2, "READY");
      setTextOfParagraph(2, "2");

      assertEquals("1",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The paragraph1 value contains");
      assertEquals("2",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The paragraph1 value contains");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='moveDown(paragraph)']"));

      assertEquals("2",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The paragraph1 value contains");
      assertEquals("1",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The paragraph1 value contains");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(2) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(2) + "//ul/li/a[@ng-click='moveUp(paragraph)']"));

      assertEquals("1",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The paragraph1 value contains");
      assertEquals("2",
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'editor')]"))
          .getText(),
        "The paragraph1 value contains");
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testMoveUpAndDown ", e);
    }

  }

  @Test
  void testDisableParagraphRunButton() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      setTextOfParagraph(1, "println (\"abcd\")");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click='toggleEnableDisable(paragraph)']"));
      assertFalse(manager.getWebDriver()
        .findElement(
          By.xpath(getParagraphXPath(1) + "//span[@class='icon-control-play shortcut-icon']"))
        .isDisplayed(), "The play button class was displayed");

      manager.getWebDriver()
        .findElement(By.xpath(".//*[@id='main']//button[contains(@ng-click, 'runAllParagraphs')]"))
        .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, false);
      manager.getWebDriver()
        .findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(2000, false);

      assertEquals("FINISHED", getParagraphStatus(1), "Paragraph status is wrong");

      manager.getWebDriver().navigate().refresh();
      ZeppelinITUtils.sleep(3000, false);
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testDisableParagraphRunButton ", e);
    }
  }

  @Test
  void testRunAllUserCodeFail() throws Exception {
    try {
      createNewNote();
      waitForParagraph(1, "READY");
      setTextOfParagraph(1, "syntax error");
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
              .click();
      waitForParagraph(2, "READY");
      setTextOfParagraph(2, "println (\"abcd\")");


      manager.getWebDriver()
        .findElement(By.xpath(".//*[@id='main']//button[contains(@ng-click, 'runAllParagraphs')]"))
        .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, false);
      manager.getWebDriver()
        .findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
              "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(2000, false);


      assertEquals("ERROR", getParagraphStatus(1), "First paragraph status is wrong");
      assertEquals("READY", getParagraphStatus(2), "Second paragraph status is wrong");

      String xpathToOutputField = getParagraphXPath(2) + "//div[contains(@id,\"_text\")]";
      assertEquals(0, manager.getWebDriver().findElements(By.xpath(xpathToOutputField)).size(),
        "Second paragraph output is wrong");


      manager.getWebDriver().navigate().refresh();
      ZeppelinITUtils.sleep(3000, false);
      deleteTestNotebook(manager.getWebDriver());
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testRunAllUserCodeFail ", e);
    }
  }

  @Test
  void testRunAllCancel() throws Exception {
    try {
      createNewNote();
      waitForParagraph(1, "READY");
      setTextOfParagraph(1, "%sh\\n\\nfor i in {1..30}; do\\n  sleep 1\\ndone");
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
              .click();
      waitForParagraph(2, "READY");
      setTextOfParagraph(2, "%sh\\n echo \"Hello World!\"");


      manager.getWebDriver()
        .findElement(By.xpath(".//*[@id='main']//button[contains(@ng-click, 'runAllParagraphs')]"))
        .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, false);
      manager.getWebDriver()
        .findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
              "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      waitForParagraph(1, "RUNNING");

      ZeppelinITUtils.sleep(2000, false);
      cancelParagraph(1);
      waitForParagraph(1, "ABORT");

      assertEquals("ABORT", getParagraphStatus(1), "First paragraph status is wrong");
      assertEquals("READY", getParagraphStatus(2), "Second paragraph status is wrong");

      manager.getWebDriver().navigate().refresh();
      ZeppelinITUtils.sleep(3000, false);
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testRunAllCancel", e);
    }
  }

  @Test
  void testRunOnSelectionChange() throws Exception {
    try {
      String xpathToRunOnSelectionChangeCheckbox = getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]";
      String xpathToDropdownMenu = getParagraphXPath(1) + "//select";
      String xpathToResultText = getParagraphXPath(1) + "//div[contains(@id,\"_html\")]";

      createNewNote();

      waitForParagraph(1, "READY");
      setTextOfParagraph(1, "%md My selection is ${my selection=1,1|2|3}");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // 1. 'RunOnSelectionChange' is true by default
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      assertTrue(
        manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
          + "//ul/li/form/input[contains(@ng-click, 'turnOnAutoRun(paragraph)')]")).isDisplayed(),
        "'Run on selection change' checkbox will be shown under dropdown menu");

      Select dropDownMenu =
        new Select(manager.getWebDriver().findElement(By.xpath((xpathToDropdownMenu))));
      dropDownMenu.selectByVisibleText("2");
      waitForParagraph(1, "FINISHED");
      assertEquals("My selection is 2",
        manager.getWebDriver().findElement(By.xpath(xpathToResultText)).getText(),
        "If 'RunOnSelectionChange' is true, the paragraph result will be updated right after click any options in the dropdown menu ");
      // 2. set 'RunOnSelectionChange' to false
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver().findElement(By.xpath(xpathToRunOnSelectionChangeCheckbox)).click();
      assertTrue(
        manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1)
            + "//ul/li/span[contains(@ng-if, 'paragraph.config.runOnSelectionChange == false')]"))
          .isDisplayed(),
        "If 'Run on selection change' checkbox is unchecked, 'paragraph.config.runOnSelectionChange' will be false ");

      Select sameDropDownMenu =
        new Select(manager.getWebDriver().findElement(By.xpath((xpathToDropdownMenu))));
      sameDropDownMenu.selectByVisibleText("1");
      waitForParagraph(1, "FINISHED");
      assertEquals("My selection is 2",
        manager.getWebDriver().findElement(By.xpath(xpathToResultText)).getText(),
        "If 'RunOnSelectionChange' is false, the paragraph result won't be updated even if we select any options in the dropdown menu ");

      // run paragraph manually by pressing ENTER
      manager.getWebDriver().findElement(By.xpath(xpathToDropdownMenu)).sendKeys(Keys.ENTER);
      waitForParagraph(1, "FINISHED");
      assertEquals("My selection is 1",
        manager.getWebDriver().findElement(By.xpath(xpathToResultText)).getText(),
        "Even if 'RunOnSelectionChange' is set as false, still can run the paragraph by pressing ENTER ");
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testRunOnSelectionChange ", e);
    }
  }

  @Test
  void testClearOutputButton() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      String xpathToOutputField = getParagraphXPath(1) + "//div[contains(@id,\"_text\")]";
      setTextOfParagraph(1, "println (\"abcd\")");
      assertEquals(0, manager.getWebDriver().findElements(By.xpath(xpathToOutputField)).size(),
        "Before Run Output field contains nothing");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("abcd",
        manager.getWebDriver().findElement(By.xpath(xpathToOutputField)).getText(),
        "After Run Output field contains the wrong output");
      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) +
          "//ul/li/a[@ng-click='clearParagraphOutput(paragraph)']"));
      ZeppelinITUtils.sleep(1000, false);
      assertEquals(0, manager.getWebDriver().findElements(By.xpath(xpathToOutputField)).size(),
        "After Clear  Output field contains something");
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testClearOutputButton ", e);
    }
  }

  @Test
  void testWidth() throws Exception {
    try {
      createNewNote();
      waitForParagraph(1, "READY");
      assertTrue(manager.getWebDriver().findElement(By.xpath("//div[contains(@class,'col-md-12')]"))
        .isDisplayed(), "Default Width is not 12");
      for (Integer newWidth = 1; newWidth <= 11; newWidth++) {
        clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
        String visibleText = newWidth.toString();
        new Select(manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
            + "//ul/li/a/select[(@ng-model='paragraph.config.colWidth')]"))).selectByVisibleText(visibleText);
        assertTrue(manager.getWebDriver()
          .findElement(By.xpath("//div[contains(@class,'col-md-" + newWidth + "')]"))
          .isDisplayed(), "New Width is not : " + newWidth);
      }
      deleteTestNotebook(manager.getWebDriver());
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testWidth ", e);
    }
  }

  @Test
  void testFontSize() throws Exception {
    try {
      createNewNote();
      waitForParagraph(1, "READY");
      Float height = Float.valueOf(
        manager.getWebDriver().findElement(By.xpath("//div[contains(@class,'ace_content')]"))
          .getCssValue("height").replace("px", ""));
      for (Integer newFontSize = 10; newFontSize <= 20; newFontSize++) {
        clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
        String visibleText = newFontSize.toString();
        new Select(manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
            + "//ul/li/a/select[(@ng-model='paragraph.config.fontSize')]"))).selectByVisibleText(visibleText);
        Float newHeight = Float.valueOf(
          manager.getWebDriver().findElement(By.xpath("//div[contains(@class,'ace_content')]"))
            .getCssValue("height").replace("px", ""));
        assertTrue(newHeight > height, "New Font size is not: " + newFontSize);
        height = newHeight;
      }
      deleteTestNotebook(manager.getWebDriver());
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testFontSize ", e);
    }
  }

  @Test
  void testTitleButton() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");

      String xpathToTitle = getParagraphXPath(1) + "//div[contains(@class, 'title')]/div";
      String xpathToSettingIcon = getParagraphXPath(1) + "//span[@class='icon-settings']";
      String xpathToShowTitle = getParagraphXPath(1) + "//ul/li/a[@ng-show='!paragraph.config.title']";
      String xpathToHideTitle = getParagraphXPath(1) + "//ul/li/a[@ng-show='paragraph.config.title']";

      ZeppelinITUtils.turnOffImplicitWaits(manager.getWebDriver());
      int titleElems = manager.getWebDriver().findElements(By.xpath(xpathToTitle)).size();
      assertEquals(0, titleElems, "Before Show Title : The title doesn't exist");
      ZeppelinITUtils.turnOnImplicitWaits(manager.getWebDriver());

      clickAndWait(By.xpath(xpathToSettingIcon));
      String titleElement =
        manager.getWebDriver().findElement(By.xpath(xpathToShowTitle)).getText();
      assertTrue(titleElement.endsWith("Show title"), titleElement);
      assertTrue(titleElement.contains("Ctrl+"), titleElement);
      assertTrue(StringUtils.containsAny(titleElement, "Option", "Alt", "+T"), titleElement);

      clickAndWait(By.xpath(xpathToShowTitle));
      assertEquals("Untitled", manager.getWebDriver().findElement(By.xpath(xpathToTitle)).getText(),
        "After Show Title : The title field contains the wrong content");

      clickAndWait(By.xpath(xpathToSettingIcon));
      titleElement =
          manager.getWebDriver().findElement(By.xpath(xpathToHideTitle)).getText();
      assertTrue(titleElement.endsWith("Hide title"), titleElement);
      assertTrue(titleElement.contains("Ctrl+"), titleElement);
      assertTrue(StringUtils.containsAny(titleElement, "Option", "Alt", "+T"), titleElement);

      clickAndWait(By.xpath(xpathToHideTitle));
      ZeppelinITUtils.turnOffImplicitWaits(manager.getWebDriver());
      titleElems = manager.getWebDriver().findElements(By.xpath(xpathToTitle)).size();
      assertEquals(0, titleElems, "After Hide Title : The title field is not hidden");
      ZeppelinITUtils.turnOnImplicitWaits(manager.getWebDriver());

      manager.getWebDriver().findElement(By.xpath(xpathToSettingIcon)).click();
      manager.getWebDriver().findElement(By.xpath(xpathToShowTitle)).click();

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'title')]")).click();
      manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) + "//input"))
        .sendKeys("NEW TITLE" + Keys.ENTER);
      ZeppelinITUtils.sleep(500, false);
      assertEquals("NEW TITLE",
        manager.getWebDriver().findElement(By.xpath(xpathToTitle)).getText(),
        "After Editing the Title : The title field contains the wrong content");
      manager.getWebDriver().navigate().refresh();
      ZeppelinITUtils.sleep(1000, false);
      assertEquals("NEW TITLE",
        manager.getWebDriver().findElement(By.xpath(xpathToTitle)).getText(),
        "After Page Refresh : The title field contains the wrong content");
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testTitleButton  ", e);
    }

  }

  @Test
  void testShowAndHideLineNumbers() throws Exception {
    try {
      createNewNote();

      waitForParagraph(1, "READY");
      String xpathToLineNumberField = getParagraphXPath(1) + "//div[contains(@class, 'ace_gutter-layer')]";
      String xpathToShowLineNumberButton = getParagraphXPath(1) + "//ul/li/a[@ng-click='showLineNumbers(paragraph)']";
      String xpathToHideLineNumberButton = getParagraphXPath(1) + "//ul/li/a[@ng-click='hideLineNumbers(paragraph)']";

      assertFalse(
        manager.getWebDriver().findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
        "Before \"Show line number\" the Line Number is Enabled");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      String showlineNumberButtonText =
        manager.getWebDriver().findElement(By.xpath(xpathToShowLineNumberButton)).getText();
      assertTrue(showlineNumberButtonText.contains("Show line numbers"), showlineNumberButtonText);
      assertTrue(showlineNumberButtonText.contains("Ctrl+"), showlineNumberButtonText);
      assertTrue(StringUtils.containsAny(showlineNumberButtonText, "Option", "Alt", "+M"),
        showlineNumberButtonText);

      clickAndWait(By.xpath(xpathToShowLineNumberButton));
      assertTrue(manager.getWebDriver().findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
        "After \"Show line number\" the Line Number is not enabled ");

      clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
      String hideLineNumerButtonText =
        manager.getWebDriver().findElement(By.xpath(xpathToHideLineNumberButton)).getText();
      assertTrue(hideLineNumerButtonText.contains("Hide line numbers"), hideLineNumerButtonText);
      assertTrue(hideLineNumerButtonText.contains("Ctrl+"), hideLineNumerButtonText);
      assertTrue(StringUtils.containsAny(hideLineNumerButtonText, "Option", "Alt", "+M"),
        hideLineNumerButtonText);

      clickAndWait(By.xpath(xpathToHideLineNumberButton));
      assertFalse(
        manager.getWebDriver().findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
        "After \"Hide line number\" the Line Number is Enabled");
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testShowAndHideLineNumbers ", e);
    }
  }

  @Test
  void testEditOnDoubleClick() throws Exception {
    try {
      createNewNote();
      Actions action = new Actions(manager.getWebDriver());

      waitForParagraph(1, "READY");

      setTextOfParagraph(1, "%md");
      manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) + "//textarea"))
        .sendKeys(Keys.ARROW_RIGHT);
      manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) + "//textarea"))
        .sendKeys(Keys.ENTER);
      manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) + "//textarea"))
        .sendKeys("# abc");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      assertEquals(0, manager.getWebDriver()
        .findElements(By
          .xpath(getParagraphXPath(1) + "//div[contains(@ng-if, 'paragraph.config.editorHide')]"))
        .size(), "Markdown editor is not hidden after run");

      assertTrue(manager.getWebDriver()
          .findElement(By.xpath(
            getParagraphXPath(1) + "//div[contains(@ng-show, 'paragraph.config.tableHide')]"))
        .isDisplayed(), "Markdown editor is not shown after run");

      // to check if editOnDblClick field is fetched correctly after refresh
      manager.getWebDriver().navigate().refresh();
      waitForParagraph(1, "FINISHED");

      action.doubleClick(manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1))))
        .perform();
      ZeppelinITUtils.sleep(1000, false);

      assertTrue(manager.getWebDriver()
        .findElement(By
          .xpath(getParagraphXPath(1) + "//div[contains(@ng-if, 'paragraph.config.editorHide')]"))
        .isDisplayed(), "Markdown editor is not shown after double click");

      assertFalse(manager.getWebDriver()
        .findElement(By.xpath(
          getParagraphXPath(1) + "//div[contains(@ng-show, 'paragraph.config.tableHide')]"))
        .isDisplayed(), "Markdown editor is not hidden after double click");

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testEditOnDoubleClick ", e);
    }
  }

  @Test
  void testSingleDynamicFormTextInput() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Hello \"+z.textbox(\"name\", \"world\")) ");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Hello world", manager.getWebDriver()
        .findElement(
          By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
        .getText(), "Output text is not equal to value specified initially");

      manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) + "//input")).clear();
      manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) + "//input"))
        .sendKeys("Zeppelin");

      assertEquals("Hello world", manager.getWebDriver()
        .findElement(
          By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
        .getText(), "After new data in text input form, output changed");

      runParagraph(1);
      ZeppelinITUtils.sleep(1000, false);
      waitForParagraph(1, "FINISHED");
      assertEquals("Hello Zeppelin", manager.getWebDriver()
        .findElement(
          By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
        .getText(), "After running the paragraph, we can not see the newly updated output");
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testSingleDynamicFormTextInput  ", e);
    }
  }

  @Test
  void testSingleDynamicFormSelectForm() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Howdy \"+z.select(\"names\", Seq((\"1\",\"Alice\"), " +
              "(\"2\",\"Bob\"),(\"3\",\"stranger\"))))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Howdy 1", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "Output text should not display any of the options in select form");

      Select dropDownMenu = new Select(manager.getWebDriver()
        .findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[1]"))));

      dropDownMenu.selectByVisibleText("Alice");
      assertEquals("Howdy 1", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "After selection in drop down menu, output should display the newly selected option");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]"));

      Select sameDropDownMenu = new Select(manager.getWebDriver()
        .findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[1]"))));
      sameDropDownMenu.selectByVisibleText("Bob");
      assertEquals("Howdy 1", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "After 'Run on selection change' checkbox is unchecked, the paragraph should not run if selecting a different option");

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testSingleDynamicFormSelectForm  ", e);
    }
  }

  @Test
  void testSingleDynamicFormCheckboxForm() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark val options = Seq((\"han\",\"Han\"), (\"leia\",\"Leia\"), " +
              "(\"luke\",\"Luke\")); println(\"Greetings \"+z.checkbox(\"skywalkers\",options).mkString(\" and \"))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().contains("Greetings han and leia and luke"),
          manager.getWebDriver()
          .findElement(
                  By.xpath(
                      getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      WebElement firstCheckbox = manager.getWebDriver()
        .findElement(By.xpath("(" + getParagraphXPath(1) + "//input[@type='checkbox'])[1]"));
      firstCheckbox.click();
      ZeppelinITUtils.sleep(2000, false);
      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().contains("Greetings leia and luke"),
          manager.getWebDriver()
              .findElement(
                  By.xpath(
                      getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]"));

      WebElement secondCheckbox = manager.getWebDriver()
        .findElement(By.xpath("(" + getParagraphXPath(1) + "//input[@type='checkbox'])[2]"));
      secondCheckbox.click();
      ZeppelinITUtils.sleep(2000, false);
      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().contains("Greetings leia and luke"),
          manager.getWebDriver()
              .findElement(
                  By.xpath(
                      getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      runParagraph(1);
      waitForParagraph(1, "FINISHED");


      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testSingleDynamicFormCheckboxForm  ", e);
    }
  }

  @Test
  void testMultipleDynamicFormsSameType() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Howdy \"+z.select(\"fruits\", Seq((\"1\",\"Apple\")," +
              "(\"2\",\"Orange\"),(\"3\",\"Peach\")))); println(\"Howdy \"+z.select(\"planets\", " +
              "Seq((\"1\",\"Venus\"),(\"2\",\"Earth\"),(\"3\",\"Mars\"))))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Howdy 1\nHowdy 1", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "Output text display any of the options in select form");

      Select dropDownMenu = new Select(manager.getWebDriver()
        .findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[1]"))));
      dropDownMenu.selectByVisibleText("Apple");
      assertEquals("Howdy 1\nHowdy 1", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "After selection in drop down menu, output doesn't display the new option we selected");

      manager.getWebDriver()
        .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]"));

      Select sameDropDownMenu = new Select(manager.getWebDriver()
        .findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[2]"))));
      sameDropDownMenu.selectByVisibleText("Earth");
      waitForParagraph(1, "FINISHED");
      assertEquals("Howdy 1\nHowdy 1", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "After 'Run on selection change' checkbox is unchecked, the paragraph should not run if selecting a different option");

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testMultipleDynamicFormsSameType  ", e);
    }
  }

  @Test
  void testNoteDynamicFormTextInput() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Hello \"+z.noteTextbox(\"name\", \"world\")) ");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Hello world", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "Output text is not equal to value specified initially");
      manager.getWebDriver().findElement(By.xpath(getNoteFormsXPath() + "//input")).clear();
      manager.getWebDriver().findElement(By.xpath(getNoteFormsXPath() + "//input"))
        .sendKeys("Zeppelin");
      manager.getWebDriver().findElement(By.xpath(getNoteFormsXPath() + "//input"))
        .sendKeys(Keys.RETURN);

      assertEquals("Hello world", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "After new data in text input form, output has changed");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Hello Zeppelin", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "After running the paragraph, we can not see the newly updated output");

      setTextOfParagraph(2, "%spark println(\"Hello \"+z.noteTextbox(\"name\", \"world\")) ");
      runParagraph(2);
      waitForParagraph(2, "FINISHED");
      assertEquals("Hello Zeppelin", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "Running the another paragraph with same form, we can not see value from note form");

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testNoteDynamicFormTextInput  ", e);
    }
  }

  @Test
  @Disabled("Doesn't work")
  void testNoteDynamicFormSelect() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Howdy \"+z.noteSelect(\"names\", Seq((\"1\",\"Alice\"), " +
          "(\"2\",\"Bob\"),(\"3\",\"stranger\"))))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Howdy ", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "Output text display any of the options in select form");

      Select dropDownMenu = new Select(
        manager.getWebDriver().findElement(By.xpath("(" + (getNoteFormsXPath() + "//select)[1]"))));

      dropDownMenu.selectByVisibleText("Bob");
      assertEquals("Howdy ", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "After selection in drop down menu, output has changed");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertEquals("Howdy 2", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(), "After run paragraph again, we can't see the newly updated output");

      setTextOfParagraph(2, "%spark println(\"Howdy \"+z.noteSelect(\"names\", Seq((\"1\",\"Alice\"), " +
          "(\"2\",\"Bob\"),(\"3\",\"stranger\"))))");

      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      assertEquals("Howdy 2", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText(),
          "Running the another paragraph with same form, we can't see value from note form");

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testNoteDynamicFormSelect  ", e);
    }
  }

  @Test
  void testDynamicNoteFormCheckbox() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark val options = Seq((\"han\",\"Han\"), (\"leia\",\"Leia\"), " +
          "(\"luke\",\"Luke\")); println(\"Greetings \"+z.noteCheckbox(\"skywalkers\",options).mkString(\" and \"))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().contains("Greetings han and leia and luke"),
          manager.getWebDriver()
              .findElement(
                  By.xpath(
                      getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      WebElement firstCheckbox = manager.getWebDriver()
        .findElement(By.xpath("(" + getNoteFormsXPath() + "//input[@type='checkbox'])[1]"));
      firstCheckbox.click();
      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().contains("Greetings han and leia and luke"),
          manager.getWebDriver()
              .findElement(
                  By.xpath(
                      getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      ZeppelinITUtils.sleep(1000, false);
      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
            .getText().contains("Greetings leia and luke"), manager.getWebDriver()
            .findElement(
                By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      setTextOfParagraph(2, "%spark val options = Seq((\"han\",\"Han\"), (\"leia\",\"Leia\"), " +
          "(\"luke\",\"Luke\")); println(\"Greetings \"+z.noteCheckbox(\"skywalkers\",options).mkString(\" and \"))");

      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      assertTrue(manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().contains("Greetings leia and luke"),
          manager.getWebDriver()
              .findElement(
                  By.xpath(
                      getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]"))
              .getText());

      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testDynamicNoteFormCheckbox  ", e);
    }
  }

  @Test
  void testWithNoteAndParagraphDynamicFormTextInput() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(z.noteTextbox(\"name\", \"note\") + \" \" + z.textbox(\"name\", \"paragraph\")) ");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      assertEquals("note paragraph", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText());
      deleteTestNotebook(manager.getWebDriver());

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testWithNoteAndParagraphDynamicFormTextInput  ", e);
    }
  }
}
