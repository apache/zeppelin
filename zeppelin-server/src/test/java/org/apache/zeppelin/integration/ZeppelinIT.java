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

import java.util.List;

import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.apache.zeppelin.notebook.Folder;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test Zeppelin with web browser.
 *
 * To test, ZeppelinServer should be running on port 8080
 * On OSX, you'll need firefox 42.0 installed, then you can run with
 *
 * PATH=~/Applications/Firefox.app/Contents/MacOS/:$PATH TEST_SELENIUM="" \
 *    mvn -Dtest=org.apache.zeppelin.integration.ZeppelinIT -Denforcer.skip=true \
 *    test -pl zeppelin-server
 *
 */
public class ZeppelinIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinIT.class);

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
  public void testAngularDisplay() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      /*
       * print angular template
       * %angular <div id='angularTestButton' ng-click='myVar=myVar+1'>BindingTest_{{myVar}}_</div>
       */
      setTextOfParagraph(1, "println(\"%angular <div id=\\'angularTestButton\\' ng-click=\\'myVar=myVar+1\\'>BindingTest_{{myVar}}_</div>\")");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // check expected text
      waitForText("BindingTest__", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind variable
       * z.angularBind("myVar", 1)
       */
      assertEquals(1, driver.findElements(By.xpath(getParagraphXPath(2) + "//textarea")).size());
      setTextOfParagraph(2, "z.angularBind(\"myVar\", 1)");
      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      // check expected text
      waitForText("BindingTest_1_", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));


      /*
       * print variable
       * print("myVar="+z.angular("myVar"))
       */
      setTextOfParagraph(3, "print(\"myVar=\"+z.angular(\"myVar\"))");
      runParagraph(3);
      waitForParagraph(3, "FINISHED");

      // check expected text
      waitForText("myVar=1", By.xpath(
              getParagraphXPath(3) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      /*
       * Click element
       */
      driver.findElement(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      waitForText("BindingTest_2_", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Register watcher
       * z.angularWatch("myVar", (before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext) => {
       *   z.run(2, context)
       * }
       */
      setTextOfParagraph(4, "z.angularWatch(\"myVar\", (before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext)=>{ z.run(2, false)})");
      runParagraph(4);
      waitForParagraph(4, "FINISHED");


      /*
       * Click element, again and see watcher works
       */
      driver.findElement(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      waitForText("BindingTest_3_", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));
      waitForParagraph(3, "FINISHED");

      // check expected text by watcher
      waitForText("myVar=3", By.xpath(
              getParagraphXPath(3) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));


      /*
       * Click element, again and see watcher still works
       */
      driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();
      // check expected text
      waitForText("BindingTest_4_", By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));
      waitForParagraph(3, "FINISHED");

      // check expected text by watcher
      waitForText("myVar=4", By.xpath(
          getParagraphXPath(3) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      /*
       * Unbind
       * z.angularUnbind("myVar")
       */
      setTextOfParagraph(5, "z.angularUnbind(\"myVar\")");
      runParagraph(5);
      waitForParagraph(5, "FINISHED");

      // check expected text
      waitForText("BindingTest__",
          By.xpath(getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind again and see rebind works.
       */
      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      // check expected text
      waitForText("BindingTest_1_",
          By.xpath(getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']"))
          .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'This note will be moved to trash')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(100, true);

      LOG.info("testCreateNotebook Test executed");
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testAngularDisplay ", e);
    }
  }

  @Test
  public void testSparkInterpreterDependencyLoading() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      // navigate to interpreter page
      WebElement settingButton = driver.findElement(By.xpath("//button[@class='nav-btn dropdown-toggle ng-scope']"));
      settingButton.click();
      WebElement interpreterLink = driver.findElement(By.xpath("//a[@href='#/interpreter']"));
      interpreterLink.click();

      // add new dependency to spark interpreter
      driver.findElement(By.xpath("//div[@id='spark']//button[contains(.,'edit')]")).sendKeys(Keys.ENTER);

      WebElement depArtifact = pollingWait(By.xpath("//input[@ng-model='setting.depArtifact']"),
          MAX_BROWSER_TIMEOUT_SEC);
      String artifact = "org.apache.commons:commons-csv:1.1";
      depArtifact.sendKeys(artifact);
      driver.findElement(By.xpath("//div[@id='spark']//form//button[1]")).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to update this interpreter and restart with new settings?')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]"));

      try {
        clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to " +
            "update this interpreter and restart with new settings?')]//" +
            "div[@class='bootstrap-dialog-close-button']/button"));
      } catch (TimeoutException | StaleElementReferenceException e) {
        //Modal dialog got closed earlier than expected nothing to worry.
      }

      driver.navigate().back();
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      setTextOfParagraph(1, "import org.apache.commons.csv.CSVFormat");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // check expected text
      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[contains(@id,\"_text\")]"));

      collector.checkThat("Paragraph from ZeppelinIT of testSparkInterpreterDependencyLoading result: ",
          paragraph1Result.getText().toString(), CoreMatchers.containsString(
              "import org.apache.commons.csv.CSVFormat"
          )
      );

      //delete created notebook for cleanup.
      deleteTestNotebook(driver);
      ZeppelinITUtils.sleep(1000, false);

      // reset dependency
      settingButton.click();
      interpreterLink.click();
      driver.findElement(By.xpath("//div[@id='spark']//button[contains(.,'edit')]")).sendKeys(Keys.ENTER);
      WebElement testDepRemoveBtn = pollingWait(By.xpath("//tr[descendant::text()[contains(.,'" +
          artifact + "')]]/td[3]/button"), MAX_IMPLICIT_WAIT);
      testDepRemoveBtn.sendKeys(Keys.ENTER);
      driver.findElement(By.xpath("//div[@id='spark']//form//button[1]")).click();
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to update this interpreter and restart with new settings?')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testSparkInterpreterDependencyLoading ", e);
    }
  }

  @Test
  public void testAngularRunParagraph() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }

    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      // Create 1st paragraph
      setTextOfParagraph(1,
              "%angular <div id=\\'angularRunParagraph\\'>Run second paragraph</div>");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      waitForText("Run second paragraph", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularRunParagraph\"]"));

      // Create 2nd paragraph
      setTextOfParagraph(2, "%sh echo TEST");
      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      // Get 2nd paragraph id
      final String secondParagraphId = driver.findElement(By.xpath(getParagraphXPath(2)
              + "//div[@class=\"control ng-scope\"]//ul[@class=\"dropdown-menu dropdown-menu-right\"]/li[1]"))
              .getAttribute("textContent");

      assertTrue("Cannot find paragraph id for the 2nd paragraph", isNotBlank(secondParagraphId));

      // Update first paragraph to call z.runParagraph() with 2nd paragraph id
      setTextOfParagraph(1,
              "%angular <div id=\\'angularRunParagraph\\' ng-click=\\'z.runParagraph(\""
                      + secondParagraphId.trim()
                      + "\")\\'>Run second paragraph</div>");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // Set new text value for 2nd paragraph
      setTextOfParagraph(2, "%sh echo NEW_VALUE");

      // Click on 1 paragraph to trigger z.runParagraph() function
      driver.findElement(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularRunParagraph\"]")).click();

      waitForParagraph(2, "FINISHED");

      // Check that 2nd paragraph has been executed
      waitForText("NEW_VALUE", By.xpath(
              getParagraphXPath(2) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      //delete created notebook for cleanup.
      deleteTestNotebook(driver);
      ZeppelinITUtils.sleep(1000, true);

      LOG.info("testAngularRunParagraph Test executed");
    }  catch (Exception e) {
      handleException("Exception in ZeppelinIT while testAngularRunParagraph", e);
    }

  }

  @Test
  public void testNoteAppearInRecentAfterCreateAndOpen() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();
      String noteName = getNoteName();
      driver.navigate().back();

      //check note is first in recent list
      ZeppelinITUtils.sleep(1000, true);
      List<WebElement> recentNames = driver.
          findElements(By.xpath("//ul[@id='recent-names']//li//div//div//a"));
      assertTrue("Note " + noteName + " is not appear in recent list",
          recentNames.get(0).getText().equals(noteName));

      driver.navigate().forward();
      deleteTestNotebook(driver);
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testNoteAppearInRecentAfterCreateAndOpen", e);
    }
  }

  @Test
  public void testNoteLiftsInRecentAfterOpenItAgain() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //create note1
      createNewNote();
      String testingNoteUrl = driver.getCurrentUrl();
      driver.navigate().back();

      //create note2
      createNewNote();
      //delete note2 (it remains in recent at first place)
      deleteTestNotebook(driver);
      //open note 1 (it should lift in recent list)
      driver.navigate().to(testingNoteUrl);
      //to home page
      driver.navigate().back();
      ZeppelinITUtils.sleep(1000, true);

      assertTrue("Note with url " + testingNoteUrl
              + "is not at first place in recent list after open",
          driver.findElements(By.xpath("//ul[@id='recent-names']//li//div//div//a")).get(0)
              .getAttribute("href").equals(testingNoteUrl));

      driver.navigate().forward();
      deleteTestNotebook(driver);
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testNoteLiftsInRecentAfterOpenItAgain", e);
    }
  }

  @Test
  public void testChangeNoteNameInRecentWhenRenameInMainList() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //create note
      createNewNote();
      String url = driver.getCurrentUrl();
      url = url.substring(url.indexOf('#'));

      //back to home page
      driver.navigate().back();

      //move mouse to note name (for rename button will shown)
      Actions action = new Actions(driver);
      String noteXPath = "//ul[@id='notebook-names']//li//div//div//a[@href= '" + url + "']";
      action.moveToElement(driver.findElement(By.xpath(noteXPath)));
      action.perform();


      clickAndWait(By.xpath(noteXPath + "//..//a//i[@uib-tooltip='Rename note']"));
      WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      block.until(ExpectedConditions.visibilityOfElementLocated(By.id("renameModal")));

      //rename
      String newName = "SomeNewName";
      WebElement inputField = driver.findElement(By.xpath("//input[@ng-model='params.newName']"));
      inputField.sendKeys(Keys.chord(Keys.CONTROL, "a"));
      inputField.sendKeys(Keys.BACK_SPACE);
      inputField.sendKeys(newName);
      clickAndWait(By.xpath("//div[@class='modal-footer']//div//button[text()=' Rename ']"));

      assertTrue("Note name in recent is not equal name in main list: note url = " + url,
          driver.findElement(
              By.xpath("//ul[@id='recent-names']//li//div//div//a[@href='" + url + "']"))
              .getText().equals(newName));
    } catch (Exception e) {
      handleException
          ("Exception in ZeppelinIT while testChangeNoteNameInRecentWhenRenameInMainList", e);
    }
  }

  @Test
  public void testRemoveRecentNoteWhenRemoveFromTrash() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //create and move to trash
      createNewNote();
      String url = driver.getCurrentUrl();
      url = url.substring(url.indexOf('#'));
      deleteTestNotebook(driver);

      //open trash folder
      String trashFolderPath = "//a[text()= ' Trash ']";
      clickAndWait(By.xpath(trashFolderPath));

      //move mouse to necessary node
      Actions action = new Actions(driver);
      String testingNotePath =
          trashFolderPath + "//..//..//div//ul//li//div//div//a[@href='" + url + "']";
      action.moveToElement(driver.findElement(By.xpath(testingNotePath)));
      action.perform();

      //remove note
      clickAndWait(
          By.xpath(testingNotePath + "//..//a//i[@uib-tooltip='Remove note permanently']"));
      driver.switchTo().activeElement();
      clickAndWait(By.xpath("//button[text() = 'OK']"));

      //check note is not exist in recent list
      assertTrue("Note with url " + url + "still exists in recent list after remove",
          driver.findElements(By.xpath("//ul[@id='recent-names']//li//div//div//a[@href='" + url + "']"))
              .size() == 0);

    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testRemoveRecentNoteWhenRemoveFromTrash", e);
    }
  }

  @Test
  public void testRemoveNoteWhenRemoveContainingFolder() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //create new note in folder
      clickAndWait(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]"));
      WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteNameModal")));
      String folderName = "folderName";
      String newName = folderName + "/note";
      WebElement inputField = driver.findElement(By.xpath("//input[@ng-model='note.notename']"));
      inputField.sendKeys(newName);
      clickAndWait(By.id("createNoteButton"));
      block.until(ExpectedConditions.invisibilityOfElementLocated(By.className("pull-right")));
      String url = driver.getCurrentUrl();
      url = url.substring(url.indexOf('#'));

      //back to home page
      driver.navigate().back();

      //move folder to trash
      String folderXPath = "//a[text() = ' " + folderName + " ']";
      WebElement folderElement = driver.findElement(By.xpath(folderXPath));
      Actions action = new Actions(driver);
      action.moveToElement(folderElement);
      action.perform();
      clickAndWait(By.xpath(folderXPath + "//..//a//i[@uib-tooltip='Move folder to Trash']"));
      driver.switchTo().activeElement();
      clickAndWait(By.xpath("//button[text() = 'OK']"));

      //check note in folder has necessary name in recent
      assertTrue("Note with url " + url + " was not renamed in recent list after move" +
          " parent folder in trash", driver.findElements(
          By.xpath("//ul[@id='recent-names']//li//div//div//a[@href='" + url + "']"))
          .get(0).getText().equals(Folder.TRASH_FOLDER_ID + "/" + newName));

      //remove folder
      //open trash folder
      String trashFolderPath = "//a[text()= ' Trash ']";
      clickAndWait(By.xpath(trashFolderPath));

      //move mouse to necessary node
      String testingNotePath =
          trashFolderPath + "//..//..//div//ul//li//div//div//a[text() = ' " + folderName + " ']";
      action.moveToElement(driver.findElement(By.xpath(testingNotePath)));
      action.perform();

      //remove note
      clickAndWait(
          By.xpath(testingNotePath + "//..//a//i[@uib-tooltip='Remove folder permanently']"));
      driver.switchTo().activeElement();
      clickAndWait(By.xpath("//button[text() = 'OK']"));

      //check note is not exist in recent list
      assertTrue("Note with url " + url + "still exists in recent list after remove",
          driver.findElements(By.xpath("//ul[@id='recent-names']//li//div//div//a[@href='" + url + "']"))
              .size() == 0);

      //check note doesn't exist in recent
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testNoteAppearInRecentAfterCreateAndOpen", e);
    }
  }
}
