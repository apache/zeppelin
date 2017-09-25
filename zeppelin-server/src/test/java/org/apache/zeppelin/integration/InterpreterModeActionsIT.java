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
import org.apache.zeppelin.CommandExecutor;
import org.apache.zeppelin.ProcessData;
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
import org.openqa.selenium.WebElement;
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

public class InterpreterModeActionsIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterModeActionsIT.class);

  @Rule
  public ErrorCollector collector = new ErrorCollector();
  static String shiroPath;
  static String authShiro = "[users]\n" +
      "admin = password1, admin\n" +
      "user1 = password2, admin\n" +
      "user2 = password3, admin\n" +
      "[main]\n" +
      "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
      "securityManager.sessionManager = $sessionManager\n" +
      "securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
      "shiro.loginUrl = /api/login\n" +
      "[roles]\n" +
      "admin = *\n" +
      "[urls]\n" +
      "/api/version = anon\n" +
      "/** = authc";

  static String originalShiro = "";
  static String interpreterOptionPath = "";
  static String originalInterpreterOption = "";

  static String cmdPsPython = "ps aux | grep 'zeppelin_ipython' | grep -v 'grep' | wc -l";
  static String cmdPsInterpreter = "ps aux | grep 'zeppelin/interpreter/python/*' |" +
      " sed -E '/grep|local-repo/d' | wc -l";

  @BeforeClass
  public static void startUp() {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), new File("../").getAbsolutePath());
      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      shiroPath = conf.getRelativeDir(String.format("%s/shiro.ini", conf.getConfDir()));
      interpreterOptionPath = conf.getRelativeDir(String.format("%s/interpreter.json", conf.getConfDir()));
      File shiroFile = new File(shiroPath);
      if (shiroFile.exists()) {
        originalShiro = StringUtils.join(FileUtils.readLines(shiroFile, "UTF-8"), "\n");
      }
      FileUtils.write(shiroFile, authShiro, "UTF-8");

      File interpreterOptionFile = new File(interpreterOptionPath);
      if (interpreterOptionFile.exists()) {
        originalInterpreterOption = StringUtils.join(FileUtils.readLines(interpreterOptionFile, "UTF-8"), "\n");
      }
    } catch (IOException e) {
      LOG.error("Error in InterpreterModeActionsIT startUp::", e);
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
        File shiroFile = new File(shiroPath);
        if (StringUtils.isBlank(originalShiro)) {
          FileUtils.deleteQuietly(shiroFile);
        } else {
          FileUtils.write(shiroFile, originalShiro, "UTF-8");
        }
      }
      if (!StringUtils.isBlank(interpreterOptionPath)) {
        File interpreterOptionFile = new File(interpreterOptionPath);
        if (StringUtils.isBlank(originalInterpreterOption)) {
          FileUtils.deleteQuietly(interpreterOptionFile);
        } else {
          FileUtils.write(interpreterOptionFile, originalInterpreterOption, "UTF-8");
        }
      }
    } catch (IOException e) {
      LOG.error("Error in InterpreterModeActionsIT tearDown::", e);
    }
    ZeppelinITUtils.restartZeppelin();
    driver.quit();
  }

  private void authenticationUser(String userName, String password) {
    pollingWait(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(500, false);
    pollingWait(By.xpath("//*[@id='userName']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(userName);
    pollingWait(By.xpath("//*[@id='password']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(password);
    pollingWait(By.xpath("//*[@id='loginModalContent']//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(1000, false);
  }

  private void logoutUser(String userName) throws URISyntaxException {
    ZeppelinITUtils.sleep(500, false);
    driver.findElement(By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
        userName + "')]")).click();
    ZeppelinITUtils.sleep(500, false);
    driver.findElement(By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
        userName + "')]//a[@ng-click='navbar.logout()']")).click();
    ZeppelinITUtils.sleep(2000, false);
    if (driver.findElement(By.xpath("//*[@id='loginModal']//div[contains(@class, 'modal-header')]/button"))
        .isDisplayed()) {
      driver.findElement(By.xpath("//*[@id='loginModal']//div[contains(@class, 'modal-header')]/button")).click();
    }
    driver.get(new URI(driver.getCurrentUrl()).resolve("/#/").toString());
    ZeppelinITUtils.sleep(500, false);
  }

  private void setPythonParagraph(int num, String text) {
    setTextOfParagraph(num, "%python\\n " + text);
    runParagraph(num);
    try {
      waitForParagraph(num, "FINISHED");
    } catch (TimeoutException e) {
      waitForParagraph(num, "ERROR");
      collector.checkThat("Exception in InterpreterModeActionsIT while setPythonParagraph",
          "ERROR", CoreMatchers.equalTo("FINISHED"));
    }
  }

  @Test
  public void testGloballyAction() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //step 1: (admin) login, set 'globally in shared' mode of python interpreter, logout
      InterpreterModeActionsIT interpreterModeActionsIT = new InterpreterModeActionsIT();
      interpreterModeActionsIT.authenticationUser("admin", "password1");
      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));
      pollingWait(By.xpath("//input[contains(@ng-model, 'searchInterpreter')]"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("python");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//button[contains(@ng-click, 'valueform.$show();\n" +
          "                  copyOriginInterpreterSettingProperties(setting.id)')]"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]/div[2]/div/div/div[1]/span[1]/button"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//li/a[contains(.,'Globally')]"));
      JavascriptExecutor jse = (JavascriptExecutor)driver;
      jse.executeScript("window.scrollBy(0,250)", "");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//div/form/button[contains(@type, 'submit')]"));
      clickAndWait(By.xpath(
          "//div[@class='modal-dialog']//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      clickAndWait(By.xpath("//a[@class='navbar-brand navbar-title'][contains(@href, '#/')]"));
      interpreterModeActionsIT.logoutUser("admin");
      //step 2: (user1) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      By locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebElement element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user1noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      interpreterModeActionsIT.setPythonParagraph(1, "user=\"user1\"");
      waitForParagraph(2, "READY");
      interpreterModeActionsIT.setPythonParagraph(2, "print user");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user1"));
      String resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));

      interpreterModeActionsIT.logoutUser("user1");

      //step 3: (user2) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      waitForParagraph(1, "READY");
      interpreterModeActionsIT.setPythonParagraph(1, "user=\"user2\"");
      waitForParagraph(2, "READY");
      interpreterModeActionsIT.setPythonParagraph(2, "print user");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user2"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 4: (user1) login, come back note user1 made, run second paragraph, check result, check process,
      //restart python interpreter, check process again, logout
      //paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      waitForParagraph(2, "FINISHED");
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));

      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not testGloballyAction");
      boolean invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("0"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("0"));
      interpreterModeActionsIT.logoutUser("user1");
    } catch (Exception e) {
      handleException("Exception in InterpreterModeActionsIT while testGloballyAction ", e);
    }
  }

  @Test
  public void testPerUserScopedAction() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //step 1: (admin) login, set 'Per user in scoped' mode of python interpreter, logout
      InterpreterModeActionsIT interpreterModeActionsIT = new InterpreterModeActionsIT();
      interpreterModeActionsIT.authenticationUser("admin", "password1");
      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();

      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));
      pollingWait(By.xpath("//input[contains(@ng-model, 'searchInterpreter')]"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("python");
      ZeppelinITUtils.sleep(500, false);

      clickAndWait(By.xpath("//div[contains(@id, 'python')]//button[contains(@ng-click, 'valueform.$show();\n" +
          "                  copyOriginInterpreterSettingProperties(setting.id)')]"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]/div[2]/div/div/div[1]/span[1]/button"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//li/a[contains(.,'Per User')]"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]/div[2]/div/div/div[1]/span[2]/button"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//li/a[contains(.,'scoped per user')]"));

      JavascriptExecutor jse = (JavascriptExecutor)driver;
      jse.executeScript("window.scrollBy(0,250)", "");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//div/form/button[contains(@type, 'submit')]"));
      clickAndWait(By.xpath(
          "//div[@class='modal-dialog']//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      clickAndWait(By.xpath("//a[@class='navbar-brand navbar-title'][contains(@href, '#/')]"));

      interpreterModeActionsIT.logoutUser("admin");

      //step 2: (user1) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      By locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebElement element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user1noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);

      waitForParagraph(1, "READY");
      interpreterModeActionsIT.setPythonParagraph(1, "user=\"user1\"");
      waitForParagraph(2, "READY");
      interpreterModeActionsIT.setPythonParagraph(2, "print user");

      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user1"));

      String resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user1");

      //step 3: (user2) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //                paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '2'
      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user2noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      interpreterModeActionsIT.setPythonParagraph(1, "user=\"user2\"");
      waitForParagraph(2, "READY");
      interpreterModeActionsIT.setPythonParagraph(2, "print user");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user2"));

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("2"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 4: (user1) login, come back note user1 made, run second paragraph, check result,
      //                restart python interpreter in note, check process again, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user1"));

      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserScopedAction");
      boolean invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user1");

      //step 5: (user2) login, come back note user2 made, restart python interpreter in note, check process, logout
      //System: Check if the number of python interpreter process is '0'
      //System: Check if the number of python process is '0'
      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserScopedAction");
      invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("0"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("0"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 6: (user1) login, come back note user1 made, run first paragraph,logout
      //        (user2) login, come back note user2 made, run first paragraph, check process, logout
      //System: Check if the number of python process is '2'
      //System: Check if the number of python interpreter process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      waitForParagraph(1, "FINISHED");
      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      interpreterModeActionsIT.logoutUser("user1");

      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("2"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 7: (admin) login, restart python interpreter in interpreter tab, check process, logout
      //System: Check if the number of python interpreter process is 0
      //System: Check if the number of python process is 0
      interpreterModeActionsIT.authenticationUser("admin", "password1");
      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();

      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));
      pollingWait(By.xpath("//input[contains(@ng-model, 'searchInterpreter')]"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("python");
      ZeppelinITUtils.sleep(500, false);

      clickAndWait(By.xpath("//div[contains(@id, 'python')]" +
          "//button[contains(@ng-click, 'restartInterpreterSetting(setting.id)')]"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart this interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserScopedAction");
      invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("0"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("0"));

      interpreterModeActionsIT.logoutUser("admin");

    } catch (Exception e) {
      handleException("Exception in InterpreterModeActionsIT while testPerUserScopedAction ", e);
    }
  }

  @Test
  public void testPerUserIsolatedAction() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      //step 1: (admin) login, set 'Per user in isolated' mode of python interpreter, logout
      InterpreterModeActionsIT interpreterModeActionsIT = new InterpreterModeActionsIT();
      interpreterModeActionsIT.authenticationUser("admin", "password1");
      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));
      pollingWait(By.xpath("//input[contains(@ng-model, 'searchInterpreter')]"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("python");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//button[contains(@ng-click, 'valueform.$show();\n" +
          "                  copyOriginInterpreterSettingProperties(setting.id)')]"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]/div[2]/div/div/div[1]/span[1]/button"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//li/a[contains(.,'Per User')]"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]/div[2]/div/div/div[1]/span[2]/button"));
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//li/a[contains(.,'isolated per user')]"));
      JavascriptExecutor jse = (JavascriptExecutor)driver;
      jse.executeScript("window.scrollBy(0,250)", "");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//div/form/button[contains(@type, 'submit')]"));
      clickAndWait(By.xpath(
          "//div[@class='modal-dialog']//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      clickAndWait(By.xpath("//a[@class='navbar-brand navbar-title'][contains(@href, '#/')]"));
      interpreterModeActionsIT.logoutUser("admin");

      //step 2: (user1) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      By locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebElement element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user1noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      interpreterModeActionsIT.setPythonParagraph(1, "user=\"user1\"");
      waitForParagraph(2, "READY");
      interpreterModeActionsIT.setPythonParagraph(2, "print user");

      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user1"));

      String resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user1");

      //step 3: (user2) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //                paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '2'
      //System: Check if the number of python process is '2'
      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user2noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      interpreterModeActionsIT.setPythonParagraph(1, "user=\"user2\"");
      waitForParagraph(2, "READY");
      interpreterModeActionsIT.setPythonParagraph(2, "print user");

      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user2"));

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("2"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("2"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 4: (user1) login, come back note user1 made, run second paragraph, check result,
      //                restart python interpreter in note, check process again, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
          CoreMatchers.equalTo("user1"));

      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));

      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserIsolatedAction");
      boolean invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("1"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("1"));
      interpreterModeActionsIT.logoutUser("user1");

      //step 5: (user2) login, come back note user2 made, restart python interpreter in note, check process, logout
      //System: Check if the number of python interpreter process is '0'
      //System: Check if the number of python process is '0'
      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));

      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserIsolatedAction");
      invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("0"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("0"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 6: (user1) login, come back note user1 made, run first paragraph,logout
      //        (user2) login, come back note user2 made, run first paragraph, check process, logout
      //System: Check if the number of python process is '2'
      //System: Check if the number of python interpreter process is '2'
      interpreterModeActionsIT.authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      waitForParagraph(1, "FINISHED");
      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      interpreterModeActionsIT.logoutUser("user1");

      interpreterModeActionsIT.authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      }
      runParagraph(1);
      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Exception in InterpreterModeActionsIT while running Python Paragraph",
            "ERROR", CoreMatchers.equalTo("FINISHED"));
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("2"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("2"));
      interpreterModeActionsIT.logoutUser("user2");

      //step 7: (admin) login, restart python interpreter in interpreter tab, check process, logout
      //System: Check if the number of python interpreter process is 0
      //System: Check if the number of python process is 0
      interpreterModeActionsIT.authenticationUser("admin", "password1");
      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();

      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));
      pollingWait(By.xpath("//input[contains(@ng-model, 'searchInterpreter')]"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("python");
      ZeppelinITUtils.sleep(500, false);

      clickAndWait(By.xpath("//div[contains(@id, 'python')]" +
          "//button[contains(@ng-click, 'restartInterpreterSetting(setting.id)')]"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart this interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOG.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserIsolatedAction");
      invisibilityStatus = (new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue("interpreter setting dialog visibility status", invisibilityStatus);
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsPython,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python process is", resultProcessNum, CoreMatchers.equalTo("0"));
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(cmdPsInterpreter,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      collector.checkThat("The number of python interpreter process is", resultProcessNum, CoreMatchers.equalTo("0"));
      interpreterModeActionsIT.logoutUser("admin");
    } catch (Exception e) {
      handleException("Exception in InterpreterModeActionsIT while testPerUserIsolatedAction ", e);
    }
  }
}
