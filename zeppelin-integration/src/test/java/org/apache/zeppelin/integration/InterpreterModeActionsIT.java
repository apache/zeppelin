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

import org.apache.zeppelin.CommandExecutor;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.ProcessData;
import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.apache.zeppelin.ZeppelinITUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;


public class InterpreterModeActionsIT extends AbstractZeppelinIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterModeActionsIT.class);

  private final static String AUTH_SHIRO = "[users]\n" +
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
      "/api/cluster/address = anon\n" +
      "/** = authc";

  private final static String CMD_PS_PYTHON =
      "ps aux | grep 'kernel_server.py' | grep -v 'grep' | wc -l";
  private final static String CMD_PS_INTERPRETER_PYTHON = "ps aux | grep 'interpreter/python/*' |" +
          " sed -E '/grep/d' | wc -l";

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(InterpreterModeActionsIT.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", AUTH_SHIRO);
    zepServer.addInterpreter("python");
    zepServer.copyLogProperties();
    zepServer.copyBinDir();
    zepServer.start(true, InterpreterModeActionsIT.class.getSimpleName());
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

  private void setPythonParagraph(int num, String text) {
    setTextOfParagraph(num, "%python\\n" + text);
    runParagraph(num);
    try {
      waitForParagraph(num, "FINISHED");
    } catch (TimeoutException e) {
      waitForParagraph(num, "ERROR");
      fail("Exception in InterpreterModeActionsIT while setPythonParagraph", e);
    }
  }

  @Test
  void testGloballyAction() throws Exception {
    try {
      //step 1: (admin) login, set 'globally in shared' mode of python interpreter, logout
      authenticationUser("admin", "password1");
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
      JavascriptExecutor jse = (JavascriptExecutor) manager.getWebDriver();
      jse.executeScript("window.scrollBy(0,250)", "");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//div/form/button[contains(@type, 'submit')]"));
      clickAndWait(By.xpath(
          "//div[@class='modal-dialog']//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      clickAndWait(By.xpath("//a[@class='navbar-brand navbar-title'][contains(@href, '#/')]"));
      logoutUser("admin");
      //step 2: (user1) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user1", "password2");
      By locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebElement element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user1noteId = manager.getWebDriver().getCurrentUrl()
        .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      setPythonParagraph(1, "user=\"user1\"");
      waitForParagraph(2, "READY");
      setPythonParagraph(2, "print(user)");
      assertEquals("user1",
        manager.getWebDriver()
          .findElement(
            By.xpath(getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]"))
          .getText().trim(),
        "The output field paragraph contains");
      String resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");

      logoutUser("user1");

      //step 3: (user2) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user2", "password3");
      locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      waitForParagraph(1, "READY");
      setPythonParagraph(1, "user=\"user2\"");
      waitForParagraph(2, "READY");
      setPythonParagraph(2, "print(user)");
      assertEquals("user2", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText().trim(),
        "The output field paragraph contains");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user2");

      //step 4: (user1) login, come back note user1 made, run second paragraph, check result, check process,
      //restart python interpreter, check process again, logout
      //paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph", e);
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");

      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not testGloballyAction");
      boolean invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user1");
    } catch (Exception e) {
      handleException("Exception in InterpreterModeActionsIT while testGloballyAction ", e);
    }
  }

  @Test
  void testPerUserScopedAction() throws Exception {
    try {
      //step 1: (admin) login, set 'Per user in scoped' mode of python interpreter, logout
      authenticationUser("admin", "password1");
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

      JavascriptExecutor jse = (JavascriptExecutor) manager.getWebDriver();
      jse.executeScript("window.scrollBy(0,250)", "");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//div/form/button[contains(@type, 'submit')]"));
      clickAndWait(By.xpath(
          "//div[@class='modal-dialog']//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      clickAndWait(By.xpath("//a[@class='navbar-brand navbar-title'][contains(@href, '#/')]"));

      logoutUser("admin");

      //step 2: (user1) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user1", "password2");
      By locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebElement element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user1noteId = manager.getWebDriver().getCurrentUrl()
        .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);

      waitForParagraph(1, "READY");
      setPythonParagraph(1, "user=\"user1\"");
      waitForParagraph(2, "READY");
      setPythonParagraph(2, "print(user)");

      assertEquals("user1", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
        "The output field paragraph contains");

      String resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");

      logoutUser("user1");

      //step 3: (user2) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //                paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '2'
      authenticationUser("user2", "password3");
      locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user2noteId = manager.getWebDriver().getCurrentUrl()
        .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      setPythonParagraph(1, "user=\"user2\"");
      waitForParagraph(2, "READY");
      setPythonParagraph(2, "print(user)");
      assertEquals("user2", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
        "The output field paragraph contains");

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("2", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user2");

      //step 4: (user1) login, come back note user1 made, run second paragraph, check result,
      //                restart python interpreter in note, check process again, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph", e);
      }
      assertEquals("user1", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText(),
        "The output field paragraph contains");

      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserScopedAction");
      boolean invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user1");

      //step 5: (user2) login, come back note user2 made, restart python interpreter in note, check process, logout
      //System: Check if the number of python interpreter process is '0'
      //System: Check if the number of python process is '0'
      authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]"));
      }
      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserScopedAction");
      invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python process interpreter is wrong");
      logoutUser("user2");

      //step 6: (user1) login, come back note user1 made, run first paragraph,logout
      //        (user2) login, come back note user2 made, run first paragraph, check process, logout
      //System: Check if the number of python process is '2'
      //System: Check if the number of python interpreter process is '1'
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph");
      }
      logoutUser("user1");

      authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph", e);
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("2", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user2");

      //step 7: (admin) login, restart python interpreter in interpreter tab, check process, logout
      //System: Check if the number of python interpreter process is 0
      //System: Check if the number of python process is 0
      authenticationUser("admin", "password1");
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
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserScopedAction");
      invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python interpreter process is wrong");

      logoutUser("admin");

    } catch (Exception e) {
      handleException("Exception in InterpreterModeActionsIT while testPerUserScopedAction ", e);
    }
  }

  @Test
  void testPerUserIsolatedAction() throws Exception {
    try {
      //step 1: (admin) login, set 'Per user in isolated' mode of python interpreter, logout
      authenticationUser("admin", "password1");
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
      JavascriptExecutor jse = (JavascriptExecutor) manager.getWebDriver();
      jse.executeScript("window.scrollBy(0,250)", "");
      ZeppelinITUtils.sleep(500, false);
      clickAndWait(By.xpath("//div[contains(@id, 'python')]//div/form/button[contains(@type, 'submit')]"));
      clickAndWait(By.xpath(
          "//div[@class='modal-dialog']//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));
      clickAndWait(By.xpath("//a[@class='navbar-brand navbar-title'][contains(@href, '#/')]"));
      logoutUser("admin");

      //step 2: (user1) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user1", "password2");
      By locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      WebElement element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user1noteId = manager.getWebDriver().getCurrentUrl()
        .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      setPythonParagraph(1, "user=\"user1\"");
      waitForParagraph(2, "READY");
      setPythonParagraph(2, "print(user)");

      assertEquals("user1", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText().trim(),
        "The output field paragraph contains");

      String resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user1");

      //step 3: (user2) login, create a new note, run two paragraph with 'python', check result, check process, logout
      //                paragraph: Check if the result is 'user2' in the second paragraph
      //System: Check if the number of python interpreter process is '2'
      //System: Check if the number of python process is '2'
      authenticationUser("user2", "password3");
      locator = By.xpath("//div[contains(@class, 'col-md-4')]/div/h5/a[contains(.,'Create new" +
          " note')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        createNewNote();
      }
      String user2noteId = manager.getWebDriver().getCurrentUrl()
        .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      setPythonParagraph(1, "user=\"user2\"");
      waitForParagraph(2, "READY");
      setPythonParagraph(2, "print(user)");

      assertEquals("user2", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText().trim(),
        "The output field paragraph contains");

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("2", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("2", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user2");

      //step 4: (user1) login, come back note user1 made, run second paragraph, check result,
      //                restart python interpreter in note, check process again, logout
      //paragraph: Check if the result is 'user1' in the second paragraph
      //System: Check if the number of python interpreter process is '1'
      //System: Check if the number of python process is '1'
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph");
      }
      assertEquals("user1", manager.getWebDriver().findElement(By.xpath(
        getParagraphXPath(2) + "//div[contains(@class, 'text plainTextContent')]")).getText().trim(),
        "The output field paragraph contains");

      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));

      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserIsolatedAction");
      boolean invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("1", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user1");

      //step 5: (user2) login, come back note user2 made, restart python interpreter in note, check process, logout
      //System: Check if the number of python interpreter process is '0'
      //System: Check if the number of python process is '0'
      authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]"));
      }
      clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      clickAndWait(By.xpath("//div[@data-ng-repeat='item in interpreterBindings' and contains(., 'python')]//a"));
      clickAndWait(By.xpath("//div[@class='modal-dialog']" +
          "[contains(.,'Do you want to restart python interpreter?')]" +
          "//div[@class='bootstrap-dialog-footer-buttons']//button[contains(., 'OK')]"));

      locator = By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to restart python interpreter?')]");
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserIsolatedAction");
      invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }
      locator = By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.visibilityOfElementLocated(locator));
      if (element.isDisplayed()) {
        clickAndWait(By.xpath("//*[@id='actionbar']//span[contains(@uib-tooltip, 'Interpreter binding')]"));
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user2");

      //step 6: (user1) login, come back note user1 made, run first paragraph,logout
      //        (user2) login, come back note user2 made, run first paragraph, check process, logout
      //System: Check if the number of python process is '2'
      //System: Check if the number of python interpreter process is '2'
      authenticationUser("user1", "password2");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user1noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph");
      }
      logoutUser("user1");

      authenticationUser("user2", "password3");
      locator = By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + user2noteId + "')]");
      element =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
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
        fail("Exception in InterpreterModeActionsIT while running Python Paragraph");
      }
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("2", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("2", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("user2");

      //step 7: (admin) login, restart python interpreter in interpreter tab, check process, logout
      //System: Check if the number of python interpreter process is 0
      //System: Check if the number of python process is 0
      authenticationUser("admin", "password1");
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
      LOGGER.info("Holding on until if interpreter restart dialog is disappeared or not in testPerUserIsolatedAction");
      invisibilityStatus =
        (new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC)))
          .until(ExpectedConditions.invisibilityOfElementLocated(locator));
      if (invisibilityStatus == false) {
        assertTrue(invisibilityStatus, "interpreter setting dialog visibility status");
      }

      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python process is wrong");
      resultProcessNum = (String) CommandExecutor.executeCommandLocalHost(CMD_PS_INTERPRETER_PYTHON,
          false, ProcessData.Types_Of_Data.OUTPUT);
      resultProcessNum = resultProcessNum.trim().replaceAll("\n", "");
      assertEquals("0", resultProcessNum, "The number of python interpreter process is wrong");
      logoutUser("admin");
    } catch (Exception e) {
      handleException("Exception in InterpreterModeActionsIT while testPerUserIsolatedAction ", e);
    }
  }
}
