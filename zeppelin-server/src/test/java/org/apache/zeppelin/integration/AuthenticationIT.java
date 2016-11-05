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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import org.openqa.selenium.WebElement;
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
      "[roles]\n" +
      "admin = *\n" +
      "hr = *\n" +
      "finance = *\n" +
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

  private void authenticationUser(String userName, String password) {
    pollingWait(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li//button[contains(.,'Login')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(1000, false);
    pollingWait(By.xpath("//*[@id='userName']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(userName);
    pollingWait(By.xpath("//*[@id='password']"), MAX_BROWSER_TIMEOUT_SEC).sendKeys(password);
    pollingWait(By.xpath("//*[@id='NoteImportCtrl']//button[contains(.,'Login')]"),
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

  private void logoutUser(String userName) {
    ZeppelinITUtils.sleep(500, false);
    driver.findElement(By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
        userName + "')]")).click();
    ZeppelinITUtils.sleep(500, false);
    driver.findElement(By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
        userName + "')]//a[@ng-click='navbar.logout()']")).click();
    ZeppelinITUtils.sleep(5000, false);
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
  public void testGroupPermission() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      AuthenticationIT authenticationIT = new AuthenticationIT();
      authenticationIT.authenticationUser("finance1", "finance1");
      createNewNote();

      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);

      pollingWait(By.xpath("//span[@tooltip='Note permissions']"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      pollingWait(By.xpath(".//*[@id='selectOwners']/following::span//input"),
          MAX_BROWSER_TIMEOUT_SEC).sendKeys("finance ");
      pollingWait(By.xpath(".//*[@id='selectReaders']/following::span//input"),
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
      handleException("Exception in ParagraphActionsIT while testGroupPermission ", e);
    }
  }

}
