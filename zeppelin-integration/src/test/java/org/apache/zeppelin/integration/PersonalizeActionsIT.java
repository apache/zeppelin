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
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.apache.zeppelin.test.DownloadUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;

public class PersonalizeActionsIT extends AbstractZeppelinIT {
  private static final String AUTH_SHIRO = "[users]\n" +
      "admin = password1, admin\n" +
      "user1 = password2, user\n" +
      "[main]\n" +
      "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
      "securityManager.sessionManager = $sessionManager\n" +
      "securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
      "shiro.loginUrl = /api/login\n" +
      "[roles]\n" +
      "admin = *\n" +
      "user = *\n" +
      "[urls]\n" +
      "/api/version = anon\n" +
      "/api/cluster/address = anon\n" +
      "/** = authc";

  static String originalShiro = "";

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    String sparkHome = DownloadUtils.downloadSpark();

    zepServer = new MiniZeppelinServer(PersonalizeActionsIT.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", AUTH_SHIRO);
    zepServer.addInterpreter("md");
    zepServer.addInterpreter("python");
    zepServer.addInterpreter("spark");
    zepServer.copyLogProperties();
    zepServer.copyBinDir();
    zepServer.start(true, PersonalizeActionsIT.class.getSimpleName());
    TestHelper.configureSparkInterpreter(zepServer, sparkHome);
  }

  @BeforeEach
  public void startUp() throws IOException {
    manager = new WebDriverManager(zepServer.getZeppelinConfiguration().getServerPort());
  }

  @AfterEach
  public void tearDownManager() throws IOException {
    manager.close();
  }

  @AfterAll
  public static void tearDown() throws Exception {
    zepServer.destroy();
  }


  private void setParagraphText(String text) {
    setTextOfParagraph(1, "%md\\n # " + text);
    runParagraph(1);
    waitForParagraph(1, "FINISHED");
  }

  @Test
  void testSimpleAction() throws Exception {
    try {
      // step 1 : (admin) create a new note, run a paragraph and turn on personalized mode
      authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait =
          new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = manager.getWebDriver().getCurrentUrl()
          .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      setParagraphText("Before");
      assertEquals("Before", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]"))
          .getText());
      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and 'Before' in result of paragraph
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      wait = new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      assertEquals("Switch to personal mode (owner can change)",
          manager.getWebDriver().findElement(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@class, 'btn btn-default btn-xs ng-scope ng-hide')]"))
              .getAttribute("uib-tooltip"));
      waitForParagraph(1, "READY");
      runParagraph(1);
      assertEquals("Before", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]"))
          .getText());
      logoutUser("user1");

      // step 3 : (admin) change paragraph contents to 'After' and check result of paragraph
      authenticationUser("admin", "password1");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      }
      waitForParagraph(1, "FINISHED");
      setParagraphText("After");
      assertEquals("After", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]"))
          .getText());
      logoutUser("admin");

      // step 4 : (user1) check whether result is 'Before' or not
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      }
      assertEquals("Before", manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]"))
          .getText());
      logoutUser("user1");
    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testSimpleAction ", e);
    }
  }

  @Test
  void testGraphAction() throws Exception {
    try {
      // step 1 : (admin) create a new note, run a paragraph, change active graph to 'Bar chart', turn on personalized mode
      authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait =
          new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = manager.getWebDriver().getCurrentUrl()
          .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      setTextOfParagraph(1, "%python print(\"%table " +
              "name\\\\tsize\\\\n" +
              "sun\\\\t100\\\\n" +
              "moon\\\\t10\")");

      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        fail(
            "Exception in PersonalizeActionsIT while testGraphAction, status of 1st Spark Paragraph ");
      }

      pollingWait(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
              "]//div[@class='modal-footer']//button[contains(.,'OK')]"));

      pollingWait(By.xpath(getParagraphXPath(1) +
          "//button[contains(@uib-tooltip, 'Bar Chart')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      assertEquals("fa fa-bar-chart",
          manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
              + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"));

      logoutUser("admin");
      manager.getWebDriver().navigate().refresh();

      // step 2 : (user1) make sure it is on personalized mode and active graph is 'Bar chart',
      // try to change active graph to 'Table' and then check result
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      assertEquals("Switch to personal mode (owner can change)",
          manager.getWebDriver().findElement(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@class, 'btn btn-default btn-xs ng-scope ng-hide')]"))
              .getAttribute("uib-tooltip"));
      assertEquals("fa fa-bar-chart",
          manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
              + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"));

      pollingWait(By.xpath(getParagraphXPath(1) +
          "//button[contains(@uib-tooltip, 'Table')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      ZeppelinITUtils.sleep(1000, false);
      assertEquals("fa fa-table", manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
          + "//button[contains(@class," +
          "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"));
      logoutUser("user1");
      manager.getWebDriver().navigate().refresh();

      // step 3: (admin) Admin view is still table because of it's personalized!
      authenticationUser("admin", "password1");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
                MAX_BROWSER_TIMEOUT_SEC).click();
      }
      assertEquals("fa fa-bar-chart",
          manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1)
              + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"));

      logoutUser("admin");
    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testGraphAction ", e);
    }
  }

  @Test
  void testDynamicFormAction() throws Exception {
    try {
      // step 1 : (admin) login, create a new note, run a paragraph with data of spark tutorial, logout.
      authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait =
          new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = manager.getWebDriver().getCurrentUrl()
          .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      setTextOfParagraph(1, "%spark println(\"Status: \"+z.textbox(\"name\", \"Before\")) ");
      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        fail(
            "Exception in PersonalizeActionsIT while testDynamicFormAction, status of 1st Spark Paragraph");
      }
      assertEquals("Before", manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) +
          "//input[contains(@name, 'name')]")).getAttribute("value"));

      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and  dynamic form value is 'Before',
      // try to change dynamic form value to 'After' and then check result
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      assertEquals("Switch to personal mode (owner can change)",
          manager.getWebDriver().findElement(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@class, 'btn btn-default btn-xs ng-scope ng-hide')]"))
              .getAttribute("uib-tooltip"));

      assertEquals("Before", manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) +
          "//input[contains(@name, 'name')]")).getAttribute("value"));

      pollingWait(By.xpath(getParagraphXPath(1) +
          "//input[contains(@name, 'name')]"), MAX_BROWSER_TIMEOUT_SEC).clear();
      pollingWait(By.xpath(getParagraphXPath(1) +
          "//input[contains(@name, 'name')]"), MAX_BROWSER_TIMEOUT_SEC).sendKeys("After");

      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        fail(
            "Exception in PersonalizeActionsIT while testDynamicFormAction, status of 1st Spark Paragraph ");
      }

      assertEquals("Status: Before", manager.getWebDriver()
          .findElement(By
              .xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText());
      logoutUser("user1");

    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testGraphAction ", e);
    }
  }
}
