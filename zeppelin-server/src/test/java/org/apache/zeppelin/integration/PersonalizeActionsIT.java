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
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

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
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), "../");
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
    if (!endToEndTestEnabled()) {
      return;
    }
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

  private void authenticationUser(String userName, String password) {
    ZeppelinITUtils.sleep(300, false);
    pollingWait(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    pollingWait(By.xpath("//*[@id='userName']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(userName);
    pollingWait(By.xpath("//*[@id='password']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(password);
    pollingWait(By.xpath("//*[@id='NoteImportCtrl']//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(1000, false);
  }

  private void logoutUser(String userName) throws URISyntaxException {
    pollingWait(By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
        userName + "')]"), MAX_BROWSER_TIMEOUT_SEC).click();
    pollingWait(By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
        userName + "')]//a[@ng-click='navbar.logout()']"), MAX_BROWSER_TIMEOUT_SEC).click();

    By locator = By.xpath("//*[@id='loginModal']//div[contains(@class, 'modal-header')]/button");
    WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    if (element.isDisplayed()) {
      driver.findElement(By.xpath("//*[@id='loginModal']//div[contains(@class, 'modal-header')]/button")).click();
    }
    driver.get(new URI(driver.getCurrentUrl()).resolve("/#/").toString());
    ZeppelinITUtils.sleep(1000, false);
  }

  private void setParagraphText(String text) {
    setTextOfParagraph(1, "%md\\n # " + text);
    runParagraph(1);
    waitForParagraph(1, "FINISHED");
  }


  private void setParagraphSparkData(int number) {
    waitForParagraph(number, "READY");
    setTextOfParagraph(number, "%spark\\n " +
        "import org.apache.commons.io.IOUtils\\n" +
        "import java.net.URL\\n" +
        "import java.nio.charset.Charset\\n" +
        "val bankText = sc.parallelize(" +
        "IOUtils.toString(new URL(\"https://s3.amazonaws.com/apache-zeppelin/tutorial/bank/bank.csv\")," +
        "Charset.forName(\"utf8\")).split(\"\\\\n\"))\\n" +
        "case class Bank(age: Integer, job: String, marital: String, education: String, balance: Integer)\\n" +
        "\\n" + "val bank = bankText.map(s => s.split(\";\")).filter(s => s(0) != \"\\\\\"age\\\\\"\")" +
        ".map(s => Bank(s(0).toInt,s(1).replaceAll(\"\\\\\"\", \"\"),s(2).replaceAll(\"\\\\\"\", \"\")," +
        "s(3).replaceAll(\"\\\\\"\", \"\"),s(5).replaceAll(\"\\\\\"\", \"\").toInt)).toDF()\\n" +
        "bank.registerTempTable(\"bank\")");
    runParagraph(number);
    try {
      waitForParagraph(number, "FINISHED");
    } catch (TimeoutException e) {
      waitForParagraph(number, "ERROR");
      collector.checkThat("Exception in PersonalizeActionsIT while testGraphAction, status of 1st Spark Paragraph ",
          "ERROR", CoreMatchers.equalTo("FINISHED"));
    }
    WebElement paragraph1Result = driver.findElement(By.xpath(
        getParagraphXPath(number) + "//div[contains(@id,\"_text\")]"));

    collector.checkThat("Exception in PersonalizeActionsIT while testGraphAction, 1st Paragraph result ",
        paragraph1Result.getText().toString(), CoreMatchers.containsString(
            "import org.apache.commons.io.IOUtils"));
  }

  @Test
  public void testSimpleAction() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      // step 1 : (admin) create a new note, run a paragraph and turn on personalized mode
      PersonalizeActionsIT personalizeActionsIT = new PersonalizeActionsIT();
      personalizeActionsIT.authenticationUser("admin", "password1");
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
      personalizeActionsIT.logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and 'Before' in result of paragraph
      personalizeActionsIT.authenticationUser("user1", "password2");
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
      personalizeActionsIT.logoutUser("user1");

      // step 3 : (admin) change paragraph contents to 'After' and check result of paragraph
      personalizeActionsIT.authenticationUser("admin", "password1");
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
      personalizeActionsIT.logoutUser("admin");

      // step 4 : (user1) check whether result is 'Before' or not
      personalizeActionsIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]");
      element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      }
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));
      personalizeActionsIT.logoutUser("user1");
    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testSimpleAction ", e);
    }
  }

  @Test
  public void testGraphAction() throws Exception {
    try {
      // step 1 : (admin) create a new note, run a paragraph, change active graph to 'Bar chart', turn on personalized mode
      PersonalizeActionsIT personalizeActionsIT = new PersonalizeActionsIT();
      personalizeActionsIT.authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      personalizeActionsIT.setParagraphSparkData(1);
      setTextOfParagraph(2, "%sql \\n" +
          "select age, count(1) value\\n" +
          "from bank \\n" +
          "where age < 30 \\n" +
          "group by age \\n" +
          "order by age");
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testGraphAction, status of 2nd Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      pollingWait(By.xpath(getParagraphXPath(2) +
          "//button[contains(@uib-tooltip, 'Bar Chart')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"),
          CoreMatchers.equalTo("fa fa-bar-chart"));

      JavascriptExecutor jse = (JavascriptExecutor)driver;
      jse.executeScript("window.scrollBy(0,-250)", "");
      ZeppelinITUtils.sleep(1000, false);

      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      personalizeActionsIT.logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and active graph is 'Barchart',
      // try to change active graph to 'Table' and then check result
      personalizeActionsIT.authenticationUser("user1", "password2");
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
          driver.findElement(By.xpath(getParagraphXPath(2) + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"),
          CoreMatchers.equalTo("fa fa-bar-chart"));

      pollingWait(By.xpath(getParagraphXPath(2) +
          "//button[contains(@uib-tooltip, 'Table')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      collector.checkThat("The output of graph mode is not changed",
          driver.findElement(By.xpath(getParagraphXPath(2) + "//button[contains(@class," +
              "'btn btn-default btn-sm ng-binding ng-scope active')]//i")).getAttribute("class"),
          CoreMatchers.equalTo("fa fa-bar-chart"));
      personalizeActionsIT.logoutUser("user1");

    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testGraphAction ", e);
    }
  }
  @Test
  public void testDynamicFormAction() throws Exception {
    try {
      // step 1 : (admin) login, create a new note, run a paragraph with data of spark tutorial, logout.
      PersonalizeActionsIT personalizeActionsIT = new PersonalizeActionsIT();
      personalizeActionsIT.authenticationUser("admin", "password1");
      By locator = By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
      WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      personalizeActionsIT.setParagraphSparkData(1);
      setTextOfParagraph(2, "%sql \\n" +
          "select age, count(1) value \\n" +
          "from bank \\n" +
          "where age < ${maxAge=30} \\n" +
          "group by age \\n" +
          "order by age \\n");
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testDynamicFormAction, status of 2nd Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(2) +
              "//input[contains(@name, 'maxAge')]")).getAttribute("value"),
          CoreMatchers.equalTo("30"));

      JavascriptExecutor jse = (JavascriptExecutor)driver;
      jse.executeScript("window.scrollBy(0,-250)", "");
      ZeppelinITUtils.sleep(1000, false);

      pollingWait(By.xpath("//*[@id='actionbar']" +
          "//button[contains(@uib-tooltip, 'Switch to personal mode')]"), MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));
      personalizeActionsIT.logoutUser("admin");

      // step 2 : (user1) make sure it is on personalized mode and  dynamic form value is '30',
      // try to change dynamic form value to '10' and then check result
      personalizeActionsIT.authenticationUser("user1", "password2");
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
          driver.findElement(By.xpath(getParagraphXPath(2) +
              "//input[contains(@name, 'maxAge')]")).getAttribute("value"),
          CoreMatchers.equalTo("30"));

      pollingWait(By.xpath(getParagraphXPath(2) +
          "//input[contains(@name, 'maxAge')]"), MAX_BROWSER_TIMEOUT_SEC).clear();
      pollingWait(By.xpath(getParagraphXPath(2) +
          "//input[contains(@name, 'maxAge')]"), MAX_BROWSER_TIMEOUT_SEC).sendKeys("10");

      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testDynamicFormAction, status of 1st Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Exception in PersonalizeActionsIT while testDynamicFormAction, status of 2nd Spark Paragraph ",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }

      collector.checkThat("The output of graph mode is changed",
          driver.findElement(By.xpath(getParagraphXPath(2) +
              "//div[contains(@class, 'ui-grid-cell-contents ng-binding ng-scope')]")).getText(),
          CoreMatchers.equalTo("19"));
      personalizeActionsIT.logoutUser("user1");

    } catch (Exception e) {
      handleException("Exception in PersonalizeActionsIT while testGraphAction ", e);
    }
  }
}
