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
import org.apache.zeppelin.integration.AuthenticationIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertTrue;

public class PersonalizeActionsIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(PersonalizeActionsIT.class);

  @Rule
  public ErrorCollector collector = new ErrorCollector();
  static String shiroPath;
  static String authShiro = "[users]\n" +
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
      "/** = authc";

  static String originalShiro = "";

  @BeforeClass
  public static void startUp() {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), new File("../").getAbsolutePath());
      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      shiroPath = conf.getRelativeDir(String.format("%s/shiro.ini", conf.getConfDir()));
      File file = new File(shiroPath);
      if (file.exists()) {
        originalShiro = StringUtils.join(FileUtils.readLines(file, "UTF-8"), "\n");
      }
      FileUtils.write(file, authShiro, "UTF-8");
    } catch (IOException e) {
      LOG.error("Error in PersonalizeActionsIT startUp::", e);
    }
    ZeppelinITUtils.restartZeppelin();
    driver = WebDriverManager.getWebDriver();
  }

  @AfterClass
  public static void tearDown() {
    try {
      if (!StringUtils.isBlank(shiroPath)) {
        File file = new File(shiroPath);
        if (StringUtils.isBlank(originalShiro)) {
          FileUtils.deleteQuietly(file);
        } else {
          FileUtils.write(file, originalShiro, "UTF-8");
        }
      }
    } catch (IOException e) {
      LOG.error("Error in PersonalizeActionsIT tearDown::", e);
    }
    ZeppelinITUtils.restartZeppelin();
    driver.quit();
  }

  private void setParagraphText(String text) {
    setTextOfParagraph(1, "%md\\n # " + text);
    runParagraph(1);
    waitForParagraph(1, "FINISHED");
  }

  @Test
  public void testSimpleAction() throws Exception {
    try {
      // step 1 : (admin) create a new note, run a paragraph and turn on personalized mode
      AuthenticationIT authenticationIT = new AuthenticationIT();
      PersonalizeActionsIT personalizeActionsIT = new PersonalizeActionsIT();
      authenticationIT.authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      personalizeActionsIT.setParagraphText("Before");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));
      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      authenticationIT.logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and 'Before' in result of paragraph
      authenticationIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      collector.checkThat("The personalized mode enables",
          driver.findElement(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@class, 'btn btn-default btn-xs ng-scope ng-hide')]")).getAttribute("uib-tooltip"),
          CoreMatchers.equalTo("Switch to personal mode (owner can change)"));
      waitForParagraph(1, "READY");
      runParagraph(1);
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));
      authenticationIT.logoutUser("user1");

      // step 3 : (admin) change paragraph contents to 'After' and check result of paragraph
      authenticationIT.authenticationUser("admin", "password1");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      }
      waitForParagraph(1, "FINISHED");
      personalizeActionsIT.setParagraphText("After");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("After"));
      authenticationIT.logoutUser("admin");

      // step 4 : (user1) check whether result is 'Before' or not
      authenticationIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      }
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));
      authenticationIT.logoutUser("user1");
    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testSimpleAction ", e);
    }
  }

  @Test
  public void testGraphAction() throws Exception {
    try {
      // step 1 : (admin) create a new note, run a paragraph, change active graph to 'Bar chart', turn on personalized mode
      AuthenticationIT authenticationIT = new AuthenticationIT();
      authenticationIT.authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      setTextOfParagraph(1, "print(s\"\"\"%table\\n" +
          "name\\tsize\\n" +
          "sun\\t100\\n" +
          "moon\\t10\"\"\")");

      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testGraphAction, status of 1st Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      pollingWait(By.xpath(getParagraphXPath(1) +
          "//button[contains(@uib-tooltip, 'Bar Chart')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"),
          CoreMatchers.equalTo("fa fa-bar-chart"));

      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      authenticationIT.logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and active graph is 'Bar chart',
      // try to change active graph to 'Table' and then check result
      authenticationIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      collector.checkThat("The personalized mode enables",
          driver.findElement(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@class, 'btn btn-default btn-xs ng-scope ng-hide')]")).getAttribute("uib-tooltip"),
          CoreMatchers.equalTo("Switch to personal mode (owner can change)"));

      collector.checkThat("Make sure the output of graph mode is",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"),
          CoreMatchers.equalTo("fa fa-bar-chart"));

      pollingWait(By.xpath(getParagraphXPath(1) +
          "//button[contains(@uib-tooltip, 'Table')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      collector.checkThat("The output of graph mode is not changed",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"),
          CoreMatchers.equalTo("fa fa-bar-chart"));
      authenticationIT.logoutUser("user1");

    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testGraphAction ", e);
    }
  }

  @Test
  public void testDynamicFormAction() throws Exception {
    try {
      // step 1 : (admin) login, create a new note, run a paragraph with data of spark tutorial, logout.
      AuthenticationIT authenticationIT = new AuthenticationIT();
      authenticationIT.authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      setTextOfParagraph(1, "%spark println(\"Status: \"+z.textbox(\"name\", \"Before\")) ");
      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testDynamicFormAction, status of 1st Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(1) +
              "//input[contains(@name, 'name')]")).getAttribute("value"),
          CoreMatchers.equalTo("Before"));

      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      authenticationIT.logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and  dynamic form value is 'Before',
      // try to change dynamic form value to 'After' and then check result
      authenticationIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      collector.checkThat("The personalized mode enables",
          driver.findElement(By.xpath("//*[@id='actionbar']" +
              "//button[contains(@class, 'btn btn-default btn-xs ng-scope ng-hide')]")).getAttribute("uib-tooltip"),
          CoreMatchers.equalTo("Switch to personal mode (owner can change)"));

      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(1) +
              "//input[contains(@name, 'name')]")).getAttribute("value"),
          CoreMatchers.equalTo("Before"));

      pollingWait(By.xpath(getParagraphXPath(1) +
          "//input[contains(@name, 'name')]"), MAX_BROWSER_TIMEOUT_SEC).clear();
      pollingWait(By.xpath(getParagraphXPath(1) +
          "//input[contains(@name, 'name')]"), MAX_BROWSER_TIMEOUT_SEC).sendKeys("After");

      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testDynamicFormAction, status of 1st Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("Status: Before"));
      authenticationIT.logoutUser("user1");

    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testGraphAction ", e);
    }
  }
}
