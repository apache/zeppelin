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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;


/**
 * Created for org.apache.zeppelin.integration on 13/06/16.
 */
public class AuthenticationIT extends AbstractZeppelinIT {

  private static MiniZeppelinServer zepServer;

  private static final String AUTH_SHIRO = "[users]\n" +
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
      "anyofrolesuser = org.apache.zeppelin.utils.AnyOfRolesUserAuthorizationFilter\n" +
      "[roles]\n" +
      "admin = *\n" +
      "hr = *\n" +
      "finance = *\n" +
      "[urls]\n" +
      "/api/version = anon\n" +
      "/api/cluster/address = anon\n" +
      "/api/interpreter/** = authc, anyofrolesuser[admin, finance]\n" +
      "/** = authc";

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(AuthenticationIT.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", AUTH_SHIRO);
    zepServer.addInterpreter("jdbc");
    zepServer.start(true, AuthenticationIT.class.getSimpleName());
  }

  @BeforeEach
  public void startUpManager() throws IOException {
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

  @Test
  void testSimpleAuthentication() throws Exception {
    try {
      authenticationUser("admin", "password1");

      assertTrue(manager.getWebDriver().findElement(By.partialLinkText("Create new note"))
        .isDisplayed(), "Check is user logged in");

      logoutUser("admin");
    } catch (Exception e) {
      handleException("Exception in AuthenticationIT while testCreateNewButton ", e);
    }
  }

  @Test
  void testAnyOfRolesUser() throws Exception {
    try {
      authenticationUser("admin", "password1");

      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));

      assertTrue(pollingWait(By.xpath(
        "//div[@id='main']/div/div[2]"),
        MIN_IMPLICIT_WAIT).isDisplayed(), "Check is user has permission to view this page");

      logoutUser("admin");

      authenticationUser("finance1", "finance1");

      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));

      assertTrue(
        pollingWait(By.xpath("//div[@id='main']/div/div[2]"), MIN_IMPLICIT_WAIT).isDisplayed(),
        "Check is user has permission to view this page");

      logoutUser("finance1");

      authenticationUser("hr1", "hr1");

      pollingWait(By.xpath("//div/button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
          MAX_BROWSER_TIMEOUT_SEC).click();
      clickAndWait(By.xpath("//li/a[contains(@href, '#/interpreter')]"));

      try {
        assertTrue(
          pollingWait(By.xpath("//li[contains(@class, 'ng-toast__message')]//span/span"),
            MIN_IMPLICIT_WAIT).isDisplayed(),
          "Check is user has permission to view this page");
      } catch (TimeoutException e) {
        throw new Exception("Expected ngToast not found", e);
      }
      logoutUser("hr1");

    } catch (Exception e) {
      handleException("Exception in AuthenticationIT while testAnyOfRolesUser ", e);
    }
  }

  @Test
  void testGroupPermission() throws Exception {
    try {
      authenticationUser("finance1", "finance1");
      createNewNote();

      String noteId = manager.getWebDriver().getCurrentUrl()
        .substring(manager.getWebDriver().getCurrentUrl().lastIndexOf("/") + 1);

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
      logoutUser("finance1");

      authenticationUser("hr1", "hr1");
      try {
        WebElement element = pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC);
        assertFalse(element.isDisplayed(), "Check is user has permission to view this note link");
      } catch (Exception e) {
        //This should have failed, nothing to worry.
      }

      manager.getWebDriver().get(new URI(manager.getWebDriver().getCurrentUrl())
        .resolve("/classic/#/notebook/" + noteId).toString());

      List<WebElement> privilegesModal = manager.getWebDriver().findElements(
          By.xpath("//div[@class='modal-content']//div[@class='bootstrap-dialog-header']" +
              "//div[contains(.,'Insufficient privileges')]"));
      assertEquals(1 , privilegesModal.size(), "Check is user has permission to view this note");
      manager.getWebDriver().findElement(
          By.xpath("//div[@class='modal-content'][contains(.,'Insufficient privileges')]" +
              "//div[@class='modal-footer']//button[2]")).click();
      logoutUser("hr1");

      authenticationUser("finance2", "finance2");
      try {
        WebElement element = pollingWait(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]"),
            MAX_BROWSER_TIMEOUT_SEC);
        assertTrue(element.isDisplayed(), "Check is user has permission to view this note link");
      } catch (Exception e) {
        //This should have failed, nothing to worry.
      }

      manager.getWebDriver().get(new URI(manager.getWebDriver().getCurrentUrl())
        .resolve("/classic/#/notebook/" + noteId).toString());

      privilegesModal = manager.getWebDriver().findElements(
          By.xpath("//div[@class='modal-content']//div[@class='bootstrap-dialog-header']" +
              "//div[contains(.,'Insufficient privileges')]"));
      assertEquals(0, privilegesModal.size(), "Check is user has permission to view this note");
      deleteTestNotebook(manager.getWebDriver());
      logoutUser("finance2");


    } catch (Exception e) {
      handleException("Exception in AuthenticationIT while testGroupPermission ", e);
    }
  }

}
