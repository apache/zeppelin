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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created for org.apache.zeppelin.integration on 13/06/16.
 */
public class AuthenticationIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationIT.class);

  @Rule
  public ErrorCollector collector = new ErrorCollector();
  static String shiroPath;
  static String authShiro = "[users]\n" +
      "admin = password1, admin\n" +
      "finance1 = finance1, finance\n" +
      "finance2 = finance2, finance\n" +
      "hr1 = hr1, hr\n" +
      "hr2 = hr2, hr\n" +
      "[main]\n" +
      "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
      "securityManager.sessionManager = $sessionManager\n" +
      "securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
      "shiro.loginUrl = /api/login\n" +
      "anyofroles = org.apache.zeppelin.utils.AnyOfRolesAuthorizationFilter\n" +
      "[roles]\n" +
      "admin = *\n" +
      "hr = *\n" +
      "finance = *\n" +
      "[urls]\n" +
      "/api/version = anon\n" +
      "/api/interpreter/** = authc, anyofroles[admin, finance]\n" +
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
      LOG.error("Error in AuthenticationIT startUp::", e);
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
      LOG.error("Error in AuthenticationIT tearDown::", e);
    }
    ZeppelinITUtils.restartZeppelin();
    driver.quit();
  }

  public void authenticationUser(String userName, String password) {
    pollingWait(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(1000, false);
    pollingWait(By.xpath("//*[@id='userName']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(userName);
    pollingWait(By.xpath("//*[@id='password']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(password);
    pollingWait(By.xpath("//*[@id='loginModalContent']//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(1000, false);
  }

  private void testShowNotebookListOnNavbar() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      pollingWait(By.xpath("//li[@class='dropdown notebook-list-dropdown']"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      assertTrue(driver.findElements(By.xpath("//a[@class=\"notebook-list-item ng-scope\"]")).size() > 0);
      pollingWait(By.xpath("//li[@class='dropdown notebook-list-dropdown']"),
              MAX_BROWSER_TIMEOUT_SEC).click();
      pollingWait(By.xpath("//li[@class='dropdown notebook-list-dropdown']"),
              MAX_BROWSER_TIMEOUT_SEC).click();
    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testShowNotebookListOnNavbar ", e);
    }
  }

  public void logoutUser(String userName) throws URISyntaxException {
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

  //  @Test
  public void testSimpleAuthentication() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      AuthenticationIT authenticationIT = new AuthenticationIT();
      authenticationIT.authenticationUser("admin", "password1");

      collector.checkThat("Check is user logged in", true,
          CoreMatchers.equalTo(driver.findElement(By.partialLinkText("Create new note"))
              .isDisplayed()));

      authenticationIT.logoutUser("admin");
    } catch (Exception e) {
      handleException("Exception in AuthenticationIT while testCreateNewButton ", e);
    }
  }

  @Test
  public void testAnyOfRoles() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      AuthenticationIT authenticationIT = new AuthenticationIT();
      authenticationIT.authenticationUser("admin", "password1");

      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));

      collector.checkThat("Check is user has permission to view this page", true,
          CoreMatchers.equalTo(pollingWait(By.xpath(
              "//div[@id='main']/div/div[2]"),
              MIN_IMPLICIT_WAIT).isDisplayed())
      );

      authenticationIT.logoutUser("admin");

      authenticationIT.authenticationUser("finance1", "finance1");

      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));

      collector.checkThat("Check is user has permission to view this page", true,
          CoreMatchers.equalTo(pollingWait(By.xpath(
              "//div[@id='main']/div/div[2]"),
              MIN_IMPLICIT_WAIT).isDisplayed())
      );
      
      authenticationIT.logoutUser("finance1");

      authenticationIT.authenticationUser("hr1", "hr1");

      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));

      try {
        collector.checkThat("Check is user has permission to view this page",
            true, CoreMatchers.equalTo(
                pollingWait(By.xpath("//li[contains(@class, 'ng-toast__message')]//span/span"),
                    MIN_IMPLICIT_WAIT).isDisplayed()));
      } catch (TimeoutException e) {
        throw new Exception("Expected ngToast not found", e);
      }
      authenticationIT.logoutUser("hr1");

    } catch (Exception e) {
      handleException("Exception in AuthenticationIT while testAnyOfRoles ", e);
    }
  }

  @Test
  public void testGroupPermission() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      AuthenticationIT authenticationIT = new AuthenticationIT();
      authenticationIT.authenticationUser("finance1", "finance1");
      createNewNote();

      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);

      pollingWait(By.xpath("//span[@uib-tooltip='Note permissions']"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      pollingWait(By.xpath(".//*[@id='selectOwners']/following::span//input"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("finance ");
      pollingWait(By.xpath(".//*[@id='selectReaders']/following::span//input"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("finance ");
      pollingWait(By.xpath(".//*[@id='selectRunners']/following::span//input"),
              MAX_BROWSER_TIMEOUT_SEC).sendKeys("finance ");
      pollingWait(By.xpath(".//*[@id='selectWriters']/following::span//input"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("finance ");
      pollingWait(By.xpath("//button[@ng-click='savePermissions()']"), MAX_BROWSER_TIMEOUT_SEC)
          .sendKeys(Keys.ENTER);

      pollingWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Permissions Saved ')]" +
              "//div[@class='modal-footer']//button[contains(.,'OK')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      authenticationIT.logoutUser("finance1");

      authenticationIT.authenticationUser("hr1", "hr1");
      try {
        WebElement element = pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC);
        collector.checkThat("Check is user has permission to view this note link", false,
            CoreMatchers.equalTo(element.isDisplayed()));
      } catch (Exception e) {
        //This should have failed, nothing to worry.
      }

      driver.get(new URI(driver.getCurrentUrl()).resolve("/#/notebook/" + noteId).toString());

      List<WebElement> privilegesModal = driver.findElements(
          By.xpath("//div[@class='modal-content']//div[@class='bootstrap-dialog-header']" +
              "//div[contains(.,'Insufficient privileges')]"));
      collector.checkThat("Check is user has permission to view this note", 1,
          CoreMatchers.equalTo(privilegesModal.size()));
      driver.findElement(
          By.xpath("//div[@class='modal-content'][contains(.,'Insufficient privileges')]" +
              "//div[@class='modal-footer']//button[2]")).click();
      authenticationIT.logoutUser("hr1");

      authenticationIT.authenticationUser("finance2", "finance2");
      try {
        WebElement element = pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC);
        collector.checkThat("Check is user has permission to view this note link", true,
            CoreMatchers.equalTo(element.isDisplayed()));
      } catch (Exception e) {
        //This should have failed, nothing to worry.
      }

      driver.get(new URI(driver.getCurrentUrl()).resolve("/#/notebook/" + noteId).toString());

      privilegesModal = driver.findElements(
          By.xpath("//div[@class='modal-content']//div[@class='bootstrap-dialog-header']" +
              "//div[contains(.,'Insufficient privileges')]"));
      collector.checkThat("Check is user has permission to view this note", 0,
          CoreMatchers.equalTo(privilegesModal.size()));
      deleteTestNotebook(driver);
      authenticationIT.logoutUser("finance2");


    } catch (Exception e) {
      handleException("Exception in AuthenticationIT while testGroupPermission ", e);
    }
  }

  @Test
  public void testFolderPermissionsPropagation() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    AuthenticationIT authenticationIT = new AuthenticationIT();
    authenticationIT.authenticationUser("admin", "password1");
    String folderName = "f";
    String subfolderName = "sub";
    createNewNote('/' + folderName + '/' + subfolderName + "/note");
    String testingNoteUrl = driver.getCurrentUrl();
    driver.navigate().back();

    //find folder element
    String noteXPath = "//ul[@id='notebook-names']/div/li/div/div/div//a";
    List<WebElement> elements = driver.findElements(By.xpath(noteXPath));
    WebElement needed = null;
    for (WebElement elem: elements) {
      if (elem.getText().equals(folderName)) {
        needed = elem;
        break;
      }
    }
    assertNotNull("Cannot find folder /" + folderName, needed);
    //move mouse (for show permissions button will shown)
    Actions action = new Actions(driver);
    action.moveToElement(needed);
    action.perform();

    //add some permissions
    clickAndWait(By.xpath(noteXPath + "//..//a//i[@uib-tooltip='Show permissions']"));
    pollingWait(By.xpath(".//*[@id='selectOwners']/following::span//input"),
        MAX_BROWSER_TIMEOUT_SEC).sendKeys("admin ");
    pollingWait(By.xpath(".//*[@id='selectWriters']/following::span//input"),
        MAX_BROWSER_TIMEOUT_SEC).sendKeys("admin ");
    pollingWait(By.xpath("//button[@ng-click='savePermissions()']"), MAX_BROWSER_TIMEOUT_SEC)
        .sendKeys(Keys.ENTER);
    pollingWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Permissions Saved ')]" +
            "//div[@class='modal-footer']//button[contains(.,'OK')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    Thread.sleep(1000);
    clickAndWait(needed);

    //find subfolder element
    elements = driver.findElements(By.xpath(noteXPath));
    needed = null;
    for (WebElement elem: elements) {
      String s = elem.getText();
      if (elem.getText().equals(subfolderName)) {
        needed = elem;
        break;
      }
    }
    assertNotNull("Cannot find folder /" + folderName + '/' + subfolderName, needed);
    //move mouse (for show permissions button will shown)
    action.moveToElement(needed);
    action.perform();
    Thread.sleep(1000);
    clickAndWait(By.xpath("//ul[@id='notebook-names']/div/li/div/div/div/ul/li/div/div/div/a//i[@uib-tooltip='Show permissions']"));

    //check subfolder has the same permissions as folder
    List<String> owners = new ArrayList<>();
    List<WebElement> users = driver.findElements(By.xpath("//select[@id='selectOwners']//option"));
    for (WebElement e: users) {
      owners.add(e.getText());
    }
    assertTrue("Wrong owners on subfolder", owners.size() == 1 && owners.get(0).equals("admin"));
    List<String> writers = new ArrayList<>();
    users = driver.findElements(By.xpath("//select[@id='selectWriters']//option"));
    for (WebElement e: users) {
      writers.add(e.getText());
    }
    assertTrue("Wrong writers on subfolder", writers.size() == 1 && writers.get(0).equals("admin"));

    //try add perm to sub
    pollingWait(By.xpath(".//*[@id='selectOwners']/following::span//input"),
        MAX_BROWSER_TIMEOUT_SEC).sendKeys("user1 ");
    pollingWait(By.xpath("//button[@ng-click='savePermissions()']"), MAX_BROWSER_TIMEOUT_SEC)
        .sendKeys(Keys.ENTER);
    WebElement buttonCancel = driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Cannot change permissions ')]" +
            "//div[@class='modal-footer']//button[contains(.,'Cancel')]"));
    assertTrue(buttonCancel != null);
    buttonCancel.click();
    pollingWait(By.xpath("//div[@class='modal-dialog']//button[contains(.,'Cancel')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    pollingWait(By.xpath("//div[@class='modal-dialog']//div[@class='modal-footer']//button[contains(.,'OK')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();

    //check note permissions
    driver.navigate().to(testingNoteUrl);
    Thread.sleep(1000);
    pollingWait(By.xpath("//span[@uib-tooltip='Note permissions']"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    owners = new ArrayList<>();
    users = driver.findElements(By.xpath("//select[@id='selectOwners']//option"));
    for (WebElement e: users) {
      String s = e.getText().trim();
      if (!s.equals("")) {
        owners.add(s);
      }
    }

    assertTrue("Wrong owners on note", owners.size() == 1 && owners.get(0).equals("admin"));
    writers = new ArrayList<>();
    users = driver.findElements(By.xpath("//select[@id='selectWriters']//option"));
    for (WebElement e: users) {
      String s = e.getText().trim();
      if (!s.equals("")) {
        writers.add(s);
      }
    }
    assertTrue("Wrong writers on note", writers.size() == 1 && writers.get(0).equals("admin"));

    //try add perm to note
    pollingWait(By.xpath(".//*[@id='selectOwners']/following::span//input"),
        MAX_BROWSER_TIMEOUT_SEC).sendKeys("user1 ");
    pollingWait(By.xpath("//button[@ng-click='savePermissions()']"), MAX_BROWSER_TIMEOUT_SEC)
        .sendKeys(Keys.ENTER);
    buttonCancel = driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Cannot change permissions ')]" +
        "//div[@class='modal-footer']//button[contains(.,'Cancel')]"));
    assertTrue(buttonCancel != null);
    buttonCancel.click();
  }

}
