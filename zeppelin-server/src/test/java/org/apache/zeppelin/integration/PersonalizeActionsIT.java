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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * Created for org.apache.zeppelin.integration on 13/06/16.
 */
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

  public void setParagraphText(String text) {
    setTextOfParagraph(1, "%md");
    driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(Keys.ARROW_RIGHT);
    driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(Keys.ENTER);
    driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(Keys.SHIFT + "3");
    driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea")).sendKeys(" " + text);

    runParagraph(1);
    waitForParagraph(1, "FINISHED");
  }

  @Test
  public void testGroupPermission() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      // step1 - (admin) create a note and paragraph & turn on personalize mode
      PersonalizeActionsIT personalizeActionsIT = new PersonalizeActionsIT();
      personalizeActionsIT.authenticationUser("admin", "password1");
      createNewNote();

      String noteId = driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
      waitForParagraph(1, "READY");
      setParagraphText("Before");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));
      driver.findElement(By.xpath("//*[@id='actionbar']//button[contains(@uib-tooltip, 'Switch to personal mode')]")).click();
      clickAndWait(By.xpath("//div[@class='modal-dialog'][contains(.,'Do you want to personalize your analysis?')" +
          "]//div[@class='modal-footer']//button[contains(.,'OK')]"));

      personalizeActionsIT.logoutUser("admin");

      // step 2 : (user1) check turn on personalize mode and 'Before' in resurt of paragraph
      personalizeActionsIT.authenticationUser("user1", "password2");
      driver.findElement(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]")).click();
      /*collector.checkThat("The personalized mode enables",
          driver.findElement(By.xpath("//*[@id='actionbar']//button[contains(@uib-tooltip, 'Switch to personal mode'), " +
                  "contains(@class, 'btn btn-primary btn-xs ng-scope')]"),
          CoreMatchers.equalTo("Before"));*/

      LOG.info("sora element check : {}",
          driver.findElement(By.xpath("//*[@id='actionbar']//button[contains(@uib-tooltip, 'Switch to personal mode'), " +
              "contains(@class, 'btn btn-primary btn-xs ng-scope')]")));

      waitForParagraph(1, "READY");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));
      personalizeActionsIT.logoutUser("user1");

      //step 3 : (admin) change paragraph contents to 'After' and result of paragraph
      personalizeActionsIT.authenticationUser("admin", "password1");
      driver.findElement(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]")).click();
      waitForParagraph(1, "FINISHED");
      setParagraphText("After");
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("After"));
      personalizeActionsIT.logoutUser("admin");

      // step 4 : (user1) check whether result is 'Before' or not
      personalizeActionsIT.authenticationUser("user1", "password2");
      driver.findElement(By.xpath("//*[@id='notebook-names']//a[contains(@href, '" + noteId + "')]")).click();
      collector.checkThat("The output field paragraph contains",
          driver.findElement(By.xpath(getParagraphXPath(1) + "//div[contains(@class, 'markdown-body')]")).getText(),
          CoreMatchers.equalTo("Before"));

    } catch (Exception e) {
      handleException("Exception in ParagraphActionsIT while testGroupPermission ", e);
    }
  }

}
