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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class ZeppelinIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinIT.class);


  @BeforeEach
  public void startUp() throws IOException {
    manager = new WebDriverManager();
  }

  @AfterEach
  public void tearDown() throws IOException {
    manager.close();
  }

  @Test
  @Disabled("Doesnt work")
  void testAngularDisplay() throws Exception {
    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      /*
       * print angular template
       * %angular <div id='angularTestButton' ng-click='myVar=myVar+1'>BindingTest_{{myVar}}_</div>
       */
      setTextOfParagraph(1, "%angular <div id=\\'angularTestButton\\' ng-click=\\'myVar=myVar+1\\'>BindingTest_{{myVar}}_</div>");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // check expected text
      waitForText("BindingTest__", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind variable
       * z.angularBind("myVar", 1)
       */
      assertEquals(1,
        manager.getWebDriver().findElements(By.xpath(getParagraphXPath(2) + "//textarea")).size());
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
      clickAndWait(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

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
      clickAndWait(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

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
      clickAndWait(By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));
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

      manager.getWebDriver()
        .findElement(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']"))
          .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, false);
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'This note will be moved to trash')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]"));
      ZeppelinITUtils.sleep(100, false);

      LOG.info("testCreateNotebook Test executed");
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testAngularDisplay ", e);
    }
  }

  //It is a flaky test, disable it temporary, should fix it later. ZEPPELIN-5528
  @Test
  @Disabled("external dependency")
  void testSparkInterpreterDependencyLoading() throws Exception {
    try {
      // navigate to interpreter page
      WebElement settingButton = manager.getWebDriver()
        .findElement(By.xpath("//button[@class='nav-btn dropdown-toggle ng-scope']"));
      settingButton.click();
      WebElement interpreterLink =
        manager.getWebDriver().findElement(By.xpath("//a[@href='#/interpreter']"));
      interpreterLink.click();

      // add new dependency to spark interpreter
      manager.getWebDriver().findElement(By.xpath("//div[@id='spark']//button[contains(.,'edit')]"))
        .sendKeys(Keys.ENTER);

      WebElement depArtifact = pollingWait(By.xpath("//input[@ng-model='setting.depArtifact']"),
          MAX_BROWSER_TIMEOUT_SEC);
      String artifact = "org.apache.commons:commons-csv:1.1";
      depArtifact.sendKeys(artifact);
      manager.getWebDriver().findElement(By.xpath("//div[@id='spark']//form//button[1]")).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to update this interpreter and restart with new settings?')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]"));

      try {
        clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to " +
            "update this interpreter and restart with new settings?')]//" +
            "div[@class='bootstrap-dialog-close-button']/button"));
      } catch (TimeoutException | StaleElementReferenceException e) {
        //Modal dialog got closed earlier than expected nothing to worry.
      }

      manager.getWebDriver().navigate().back();
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      setTextOfParagraph(1, "import org.apache.commons.csv.CSVFormat");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // check expected text
      WebElement paragraph1Result = manager.getWebDriver().findElement(By.xpath(
          getParagraphXPath(1) + "//div[contains(@id,\"_text\")]"));

      assertTrue(
        paragraph1Result.getText().toString().contains("import org.apache.commons.csv.CSVFormat"),
        paragraph1Result.getText().toString());

      //delete created notebook for cleanup.
      deleteTestNotebook(manager.getWebDriver());
      ZeppelinITUtils.sleep(1000, false);

      // reset dependency
      settingButton.click();
      interpreterLink.click();
      manager.getWebDriver().findElement(By.xpath("//div[@id='spark']//button[contains(.,'edit')]"))
        .sendKeys(Keys.ENTER);
      WebElement testDepRemoveBtn = pollingWait(By.xpath("//tr[descendant::text()[contains(.,'" +
          artifact + "')]]/td[3]/button"), MAX_IMPLICIT_WAIT);
      testDepRemoveBtn.sendKeys(Keys.ENTER);
      manager.getWebDriver().findElement(By.xpath("//div[@id='spark']//form//button[1]")).click();
      manager.getWebDriver().findElement(By.xpath(
        "//div[@class='modal-dialog'][contains(.,'Do you want to update this interpreter and restart with new settings?')]"
          +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testSparkInterpreterDependencyLoading ", e);
    }
  }

  @Test
  void testAngularRunParagraph() throws Exception {
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
      final String secondParagraphId =
        manager.getWebDriver().findElement(By.xpath(getParagraphXPath(2)
              + "//div[@class=\"control ng-scope\"]//ul[@class=\"dropdown-menu dropdown-menu-right\"]/li[1]"))
              .getAttribute("textContent");

      assertTrue(isNotBlank(secondParagraphId), "Cannot find paragraph id for the 2nd paragraph");

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

      clickAndWait(By.xpath(getParagraphXPath(1) + "//div[@id=\"angularRunParagraph\"]"));

      waitForParagraph(2, "FINISHED");

      // Check that 2nd paragraph has been executed
      waitForText("NEW_VALUE", By.xpath(
              getParagraphXPath(2) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      //delete created notebook for cleanup.
      deleteTestNotebook(manager.getWebDriver());
      ZeppelinITUtils.sleep(1000, false);

      LOG.info("testAngularRunParagraph Test executed");
    }  catch (Exception e) {
      handleException("Exception in ZeppelinIT while testAngularRunParagraph", e);
    }

  }

  @Test
  void deleteTrashNode() throws Exception {
    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      String currentUrl = manager.getWebDriver().getCurrentUrl();
      LOG.info("currentUrl = " + currentUrl);

      //delete created notebook to trash
      deleteTestNotebook(manager.getWebDriver());
      ZeppelinITUtils.sleep(3000, false);

      // reopen trash note
      manager.getWebDriver().get(currentUrl);
      ZeppelinITUtils.sleep(3000, false);

      // delete note from trash
      deleteTrashNotebook(manager.getWebDriver());
      ZeppelinITUtils.sleep(2000, false);
      LOG.info("deleteTrashNode executed");
    }  catch (Exception e) {
      handleException("Exception in ZeppelinIT while deleteTrashNode", e);
    }
  }
}
