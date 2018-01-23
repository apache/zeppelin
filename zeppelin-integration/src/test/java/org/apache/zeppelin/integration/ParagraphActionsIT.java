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
    driver = WebDriverManager.getWebDriver();
  }

  @After
  public void tearDown() {
    driver.quit();
  }

  @Test
  public void testCreateNewButton() throws Exception {
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
  public void testRunOnSelectionChange() throws Exception {
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
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      collector.checkThat("'Run on selection change' checkbox will be shown under dropdown menu ",
        driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-click, 'turnOnAutoRun(paragraph)')]")).isDisplayed(),
        CoreMatchers.equalTo(true));

      Select dropDownMenu = new Select(driver.findElement(By.xpath((xpathToDropdownMenu))));
      dropDownMenu.selectByVisibleText("2");
      waitForParagraph(1, "FINISHED");
      collector.checkThat("If 'RunOnSelectionChange' is true, the paragraph result will be updated right after click any options in the dropdown menu ",
        driver.findElement(By.xpath(xpathToResultText)).getText(),
        CoreMatchers.equalTo("My selection is 2"));

      // 2. set 'RunOnSelectionChange' to false
      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      driver.findElement(By.xpath(xpathToRunOnSelectionChangeCheckbox)).click();
      collector.checkThat("If 'Run on selection change' checkbox is unchecked, 'paragraph.config.runOnSelectionChange' will be false ",
        driver.findElement(By.xpath(getParagraphXPath(1) + "//ul/li/span[contains(@ng-if, 'paragraph.config.runOnSelectionChange == false')]")).isDisplayed(),
        CoreMatchers.equalTo(true));

      Select sameDropDownMenu = new Select(driver.findElement(By.xpath((xpathToDropdownMenu))));
      sameDropDownMenu.selectByVisibleText("1");
      waitForParagraph(1, "FINISHED");
      collector.checkThat("If 'RunOnSelectionChange' is false, the paragraph result won't be updated even if we select any options in the dropdown menu ",
        driver.findElement(By.xpath(xpathToResultText)).getText(),
        CoreMatchers.equalTo("My selection is 2"));

      // run paragraph manually by pressing ENTER
      driver.findElement(By.xpath(xpathToDropdownMenu)).sendKeys(Keys.ENTER);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Even if 'RunOnSelectionChange' is set as false, still can run the paragraph by pressing ENTER ",
        driver.findElement(By.xpath(xpathToResultText)).getText(),
        CoreMatchers.equalTo("My selection is 1"));

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testRunOnSelectionChange ", e);
    }
  }

  @Test
  public void testClearOutputButton() throws Exception {
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
            + "//ul/li/a/select[(@ng-model='paragraph.config.colWidth')]"))).selectByVisibleText(visibleText);
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
  public void testFontSize() throws Exception {
    try {
      createNewNote();
      waitForParagraph(1, "READY");
      Float height = Float.valueOf(driver.findElement(By.xpath("//div[contains(@class,'ace_content')]"))
          .getCssValue("height").replace("px", ""));
      for (Integer newFontSize = 10; newFontSize <= 20; newFontSize++) {
        clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
        String visibleText = newFontSize.toString();
        new Select(driver.findElement(By.xpath(getParagraphXPath(1)
            + "//ul/li/a/select[(@ng-model='paragraph.config.fontSize')]"))).selectByVisibleText(visibleText);
        Float newHeight = Float.valueOf(driver.findElement(By.xpath("//div[contains(@class,'ace_content')]"))
            .getCssValue("height").replace("px", ""));
        collector.checkThat("New Font size is : " + newFontSize,
            newHeight > height,
            CoreMatchers.equalTo(true));
        height = newHeight;
      }
      deleteTestNotebook(driver);
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testFontSize ", e);
    }
  }

  @Test
  public void testTitleButton() throws Exception {
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
          CoreMatchers.allOf(CoreMatchers.endsWith("Show title"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+T")));

      clickAndWait(By.xpath(xpathToShowTitle));
      collector.checkThat("After Show Title : The title field contains",
          driver.findElement(By.xpath(xpathToTitle)).getText(),
          CoreMatchers.equalTo("Untitled"));

      clickAndWait(By.xpath(xpathToSettingIcon));
      collector.checkThat("After Show Title : The title option in option panel of paragraph is labeled as",
          driver.findElement(By.xpath(xpathToHideTitle)).getText(),
          CoreMatchers.allOf(CoreMatchers.endsWith("Hide title"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+T")));

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
          CoreMatchers.allOf(CoreMatchers.endsWith("Show line numbers"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+M")));


      clickAndWait(By.xpath(xpathToShowLineNumberButton));
      collector.checkThat("After \"Show line number\" the Line Number is Enabled ",
          driver.findElement(By.xpath(xpathToLineNumberField)).isDisplayed(),
          CoreMatchers.equalTo(true));

      clickAndWait(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']"));
      collector.checkThat("After \"Show line number\" The option panel in paragraph has button labeled ",
          driver.findElement(By.xpath(xpathToHideLineNumberButton)).getText(),
          CoreMatchers.allOf(CoreMatchers.endsWith("Hide line numbers"), CoreMatchers.containsString("Ctrl+"),
              CoreMatchers.anyOf(CoreMatchers.containsString("Option"), CoreMatchers.containsString("Alt")),
              CoreMatchers.containsString("+M")));

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

  @Test
  public void testSingleDynamicFormTextInput() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Hello \"+z.textbox(\"name\", \"world\")) ");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text is equal to value specified initially",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Hello world"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//input")).clear();
      driver.findElement(By.xpath(getParagraphXPath(1) + "//input")).sendKeys("Zeppelin");

      collector.checkThat("After new data in text input form, output should not be changed",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Hello world"));

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Only after running the paragraph, we can see the newly updated output",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Hello Zeppelin"));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testSingleDynamicFormTextInput  ", e);
    }
  }

  @Test
  public void testSingleDynamicFormSelectForm() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Howdy \"+z.select(\"names\", Seq((\"1\",\"Alice\"), " +
              "(\"2\",\"Bob\"),(\"3\",\"stranger\"))))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text should not display any of the options in select form",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Howdy "));

      Select dropDownMenu = new Select(driver.findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[1]"))));

      dropDownMenu.selectByVisibleText("Alice");
      collector.checkThat("After selection in drop down menu, output should display the newly selected option",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Howdy 1"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]"));

      Select sameDropDownMenu = new Select(driver.findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[1]"))));
      sameDropDownMenu.selectByVisibleText("Bob");
      collector.checkThat("After 'Run on selection change' checkbox is unchecked, the paragraph should not run if selecting a different option",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Howdy 1"));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testSingleDynamicFormSelectForm  ", e);
    }
  }

  @Test
  public void testSingleDynamicFormCheckboxForm() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark val options = Seq((\"han\",\"Han\"), (\"leia\",\"Leia\"), " +
              "(\"luke\",\"Luke\")); println(\"Greetings \"+z.checkbox(\"skywalkers\",options).mkString(\" and \"))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text should display all of the options included in check boxes",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.containsString("Greetings han and leia and luke"));

      WebElement firstCheckbox = driver.findElement(By.xpath("(" + getParagraphXPath(1) + "//input[@type='checkbox'])[1]"));
      firstCheckbox.click();
      collector.checkThat("After unchecking one of the boxes, we can see the newly updated output without the option we unchecked",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.containsString("Greetings leia and luke"));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]"));

      WebElement secondCheckbox = driver.findElement(By.xpath("(" + getParagraphXPath(1) + "//input[@type='checkbox'])[2]"));
      secondCheckbox.click();
      collector.checkThat("After 'Run on selection change' checkbox is unchecked, the paragraph should not run if check box state is modified",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.containsString("Greetings leia and luke"));

      runParagraph(1);
      waitForParagraph(1, "FINISHED");


      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testSingleDynamicFormCheckboxForm  ", e);
    }
  }

  @Test
  public void testMultipleDynamicFormsSameType() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Howdy \"+z.select(\"fruits\", Seq((\"1\",\"Apple\")," +
              "(\"2\",\"Orange\"),(\"3\",\"Peach\")))); println(\"Howdy \"+z.select(\"planets\", " +
              "Seq((\"1\",\"Venus\"),(\"2\",\"Earth\"),(\"3\",\"Mars\"))))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text should not display any of the options in select form",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Howdy \nHowdy "));

      Select dropDownMenu = new Select(driver.findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[1]"))));
      dropDownMenu.selectByVisibleText("Apple");
      collector.checkThat("After selection in drop down menu, output should display the new option we selected",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Howdy 1\nHowdy "));

      driver.findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      clickAndWait(By.xpath(getParagraphXPath(1) + "//ul/li/form/input[contains(@ng-checked, 'true')]"));

      Select sameDropDownMenu = new Select(driver.findElement(By.xpath("(" + (getParagraphXPath(1) + "//select)[2]"))));
      sameDropDownMenu.selectByVisibleText("Earth");
      waitForParagraph(1, "FINISHED");
      collector.checkThat("After 'Run on selection change' checkbox is unchecked, the paragraph should not run if selecting a different option",
              driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
              CoreMatchers.equalTo("Howdy 1\nHowdy "));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testMultipleDynamicFormsSameType  ", e);
    }
  }

  @Test
  public void testNoteDynamicFormTextInput() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Hello \"+z.noteTextbox(\"name\", \"world\")) ");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text is equal to value specified initially", driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(), CoreMatchers.equalTo("Hello world"));
      driver.findElement(By.xpath(getNoteFormsXPath() + "//input")).clear();
      driver.findElement(By.xpath(getNoteFormsXPath() + "//input")).sendKeys("Zeppelin");
      driver.findElement(By.xpath(getNoteFormsXPath() + "//input")).sendKeys(Keys.RETURN);

      collector.checkThat("After new data in text input form, output should not be changed",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Hello world"));

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Only after running the paragraph, we can see the newly updated output",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Hello Zeppelin"));

      setTextOfParagraph(2, "%spark println(\"Hello \"+z.noteTextbox(\"name\", \"world\")) ");
      runParagraph(2);
      waitForParagraph(2, "FINISHED");
      collector.checkThat("Running the another paragraph with same form, we can see value from note form",
      driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
      CoreMatchers.equalTo("Hello Zeppelin"));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testNoteDynamicFormTextInput  ", e);
    }
  }

  @Test
  public void testNoteDynamicFormSelect() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(\"Howdy \"+z.noteSelect(\"names\", Seq((\"1\",\"Alice\"), " +
          "(\"2\",\"Bob\"),(\"3\",\"stranger\"))))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text should not display any of the options in select form",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Howdy "));

      Select dropDownMenu = new Select(driver.findElement(By.xpath("(" + (getNoteFormsXPath() + "//select)[1]"))));

      dropDownMenu.selectByVisibleText("Bob");
      collector.checkThat("After selection in drop down menu, output should not be changed",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Howdy "));

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      collector.checkThat("After run paragraph again, we can see the newly updated output",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Howdy 2"));

      setTextOfParagraph(2, "%spark println(\"Howdy \"+z.noteSelect(\"names\", Seq((\"1\",\"Alice\"), " +
          "(\"2\",\"Bob\"),(\"3\",\"stranger\"))))");

      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      collector.checkThat("Running the another paragraph with same form, we can see value from note form",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Howdy 2"));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testNoteDynamicFormSelect  ", e);
    }
  }

  @Test
  public void testDynamicNoteFormCheckbox() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark val options = Seq((\"han\",\"Han\"), (\"leia\",\"Leia\"), " +
          "(\"luke\",\"Luke\")); println(\"Greetings \"+z.noteCheckbox(\"skywalkers\",options).mkString(\" and \"))");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("Output text should display all of the options included in check boxes",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.containsString("Greetings han and leia and luke"));

      WebElement firstCheckbox = driver.findElement(By.xpath("(" + getNoteFormsXPath() + "//input[@type='checkbox'])[1]"));
      firstCheckbox.click();
      collector.checkThat("After unchecking one of the boxes, output should not be changed",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.containsString("Greetings han and leia and luke"));

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      collector.checkThat("After run paragraph again, we can see the newly updated output",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.containsString("Greetings leia and luke"));

      setTextOfParagraph(2, "%spark val options = Seq((\"han\",\"Han\"), (\"leia\",\"Leia\"), " +
          "(\"luke\",\"Luke\")); println(\"Greetings \"+z.noteCheckbox(\"skywalkers\",options).mkString(\" and \"))");

      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      collector.checkThat("Running the another paragraph with same form, we can see value from note form",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.containsString("Greetings leia and luke"));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testDynamicNoteFormCheckbox  ", e);
    }
  }

  @Test
  public void testWithNoteAndParagraphDynamicFormTextInput() throws Exception {
    try {
      createNewNote();

      setTextOfParagraph(1, "%spark println(z.noteTextbox(\"name\", \"note\") + \" \" + z.textbox(\"name\", \"paragraph\")) ");

      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      collector.checkThat("After run paragraph, we can see computed output from two forms",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("note paragraph"));

      deleteTestNotebook(driver);

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testWithNoteAndParagraphDynamicFormTextInput  ", e);
    }
  }
}
