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

package org.apache.zeppelin;


import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractZeppelinIT {

  protected WebDriverManager manager;

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractZeppelinIT.class);
  protected static final long MIN_IMPLICIT_WAIT = 5;
  protected static final long MAX_IMPLICIT_WAIT = 30;
  protected static final long MAX_BROWSER_TIMEOUT_SEC = 30;
  protected static final long MAX_PARAGRAPH_TIMEOUT_SEC = 120;
  private static final String CLASSIC_LOGIN_PATH = "/classic/api/login";

  protected void authenticationUser(String userName, String password) {
    WebElement loginModal = manager.getWebDriver().findElement(By.id("loginModal"));
    if (!loginModal.isDisplayed()) {
      try {
        clickableWait(
            By.xpath("//div[contains(@class, 'navbar-collapse')]//li//button[contains(.,'Login')]"),
            MAX_BROWSER_TIMEOUT_SEC).click();
      } catch (ElementClickInterceptedException e) {
        // Authentication-required pages can open the modal between the visibility check
        // and the click. Continue only when that modal is now actually visible.
        if (!manager.getWebDriver().findElement(By.id("loginModal")).isDisplayed()) {
          throw e;
        }
      }
    }

    By userNameLocator = By.id("userName");
    By passwordLocator = By.id("password");
    WebElement userNameInput = angularModelWait(userNameLocator);
    WebElement passwordInput = angularModelWait(passwordLocator);
    WebElement loginButton = manager.getWebDriver().findElement(
        By.xpath("//*[@id='loginModalContent']//button[contains(.,'Login')]"));

    // Send both input events and click in one browser task. Bootstrap can finish a stale
    // modal transition between separate WebDriver commands and reset loginParams.
    ((JavascriptExecutor) manager.getWebDriver()).executeScript(
        "function update(element, value) {"
            + "element.value = value;"
            + "element.dispatchEvent(new Event('input', {bubbles: true}));"
            + "}"
            + "update(arguments[0], arguments[3]);"
            + "update(arguments[1], arguments[4]);"
            + "if (angular.element(arguments[0]).controller('ngModel').$viewValue"
            + " !== arguments[3] ||"
            + " angular.element(arguments[1]).controller('ngModel').$viewValue"
            + " !== arguments[4]) {"
            + "throw new Error('Login form model did not receive credentials');"
            + "}"
            + "arguments[2].click();",
        userNameInput, passwordInput, loginButton, userName, password);

    // Wait for the logged-in navbar user dropdown to appear (indicates login completed
    // and Angular digest cycle has updated the DOM), then dismiss any leftover modal overlay
    visibilityWait(
        By.xpath("//div[contains(@class, 'navbar-collapse')]//li//button[contains(@class, 'nav-btn dropdown-toggle ng-scope')]"),
        MAX_BROWSER_TIMEOUT_SEC);
    try {
      ((JavascriptExecutor) manager.getWebDriver()).executeScript(
          "$('.modal-backdrop').remove(); $('#loginModal').modal('hide');");
    } catch (Exception e) {
      // ignore if jQuery/Bootstrap not ready
    }
    ZeppelinITUtils.sleep(500, false);
  }

  private WebElement angularModelWait(By locator) {
    WebDriverWait wait = new WebDriverWait(manager.getWebDriver(),
        Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
    wait.ignoring(StaleElementReferenceException.class);
    return wait.until(driver -> {
      WebElement element = driver.findElement(locator);
      Boolean modelReady = (Boolean) ((JavascriptExecutor) driver).executeScript(
          "return !!(window.angular && angular.element(arguments[0]).controller('ngModel'));",
          element);
      return element.isDisplayed() && Boolean.TRUE.equals(modelReady) ? element : null;
    });
  }

  // Logs in by issuing a synchronous XHR POST to the REST login endpoint from inside the
  // browser's own JS context, so the browser's own session (not a separate HTTP client
  // session) becomes authenticated. Refreshing afterwards lets app.js's pre-bootstrap
  // ticket check find that same authenticated session. Skips the login modal entirely, so
  // none of its fade/WebSocket-reopen races apply. Intended for tests where login is only
  // a precondition, not the behavior under test.
  protected void authenticationUserViaRest(String userName, String password) {
    String script = "var xhr = new XMLHttpRequest();"
        + "xhr.open('POST', arguments[0], false);"
        + "xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');"
        + "xhr.send('userName=' + encodeURIComponent(arguments[1]) + "
        + "'&password=' + encodeURIComponent(arguments[2]));"
        + "return xhr.status;";
    Object result = ((JavascriptExecutor) manager.getWebDriver())
        .executeScript(script, CLASSIC_LOGIN_PATH, userName, password);
    int status = ((Number) result).intValue();
    if (status != 200) {
      throw new IllegalStateException("REST login failed with status " + status);
    }
    manager.getWebDriver().navigate().refresh();
    visibilityWait(loggedInUserMenuLocator(), MAX_BROWSER_TIMEOUT_SEC);
  }

  // Shared locator for the logged-in navbar user menu button. Uses a class-order-agnostic
  // partial match since AngularJS may render the class attribute in a different order.
  protected static By loggedInUserMenuLocator() {
    return By.xpath("//button[contains(@class, 'nav-btn') and contains(@class, 'dropdown-toggle')]");
  }

  // Extracts the note id segment from the current URL, stripping any trailing query string
  // or fragment (e.g. the "?ref=%2F" a post-refresh SPA URL appends), so callers get a clean
  // note id instead of one polluted by a query string/fragment.
  protected String extractNoteIdFromCurrentUrl() {
    String url = manager.getWebDriver().getCurrentUrl();
    String noteId = url.substring(url.lastIndexOf("/") + 1);
    int queryIndex = noteId.indexOf("?");
    if (queryIndex != -1) {
      noteId = noteId.substring(0, queryIndex);
    }
    int fragmentIndex = noteId.indexOf("#");
    if (fragmentIndex != -1) {
      noteId = noteId.substring(0, fragmentIndex);
    }
    return noteId;
  }

  protected void logoutUser(String userName) throws URISyntaxException {
    ZeppelinITUtils.sleep(500, false);
    clickableWait(
        By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" + userName + "')]"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(500, false);
    clickableWait(
        By.xpath("//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" + userName + "')]//a[@ng-click='navbar.logout()']"),
        MAX_BROWSER_TIMEOUT_SEC).click();
    ZeppelinITUtils.sleep(2000, false);
    try {
      WebElement closeButton = manager.getWebDriver().findElement(
          By.xpath("//*[@id='loginModal']//div[contains(@class, 'modal-header')]/button"));
      if (closeButton.isDisplayed()) {
        closeButton.click();
      }
    } catch (NoSuchElementException e) {
      // login modal close button not found, which is fine
    }
    manager.getWebDriver().get(new URI(manager.getWebDriver().getCurrentUrl()).resolve("/classic/#/").toString());
    ZeppelinITUtils.sleep(500, false);
  }
  
  protected void setTextOfParagraph(int paragraphNo, String text) {
    String paragraphXpath = getParagraphXPath(paragraphNo);

    try {
      manager.getWebDriver().manage().timeouts().implicitlyWait(Duration.ofMillis(100));
      // make sure ace code is visible, if not click on show editor icon to make it visible
      manager.getWebDriver()
        .findElement(By.xpath(paragraphXpath + "//span[@class='icon-size-fullscreen']")).click();
    } catch (NoSuchElementException e) {
      // ignore
    } finally {
      manager.getWebDriver().manage().timeouts()
        .implicitlyWait(Duration.ofSeconds(AbstractZeppelinIT.MAX_BROWSER_TIMEOUT_SEC));
    }
    String editorId = pollingWait(By.xpath(paragraphXpath + "//div[contains(@class, 'editor')]"),
        MIN_IMPLICIT_WAIT).getAttribute("id");
    if (manager.getWebDriver() instanceof JavascriptExecutor) {
      ((JavascriptExecutor) manager.getWebDriver())
        .executeScript("ace.edit('" + editorId + "'). setValue('" + text + "')");
    } else {
      throw new IllegalStateException("This driver does not support JavaScript!");
    }
  }

  protected void runParagraph(int paragraphNo) {
    By by = By.xpath(getParagraphXPath(paragraphNo) + "//span[@class='icon-control-play']");
    clickAndWait(by);
  }

  protected void cancelParagraph(int paragraphNo) {
    By by = By.xpath(getParagraphXPath(paragraphNo) + "//span[@class='icon-control-pause']");
    clickAndWait(by);
  }

  protected static String getParagraphXPath(int paragraphNo) {
    return "(//div[@ng-controller=\"ParagraphCtrl\"])[" + paragraphNo + "]";
  }

  protected static String getNoteFormsXPath() {
    return "(//div[@id='noteForms'])";
  }

  protected boolean waitForParagraph(final int paragraphNo, final String state) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]//span[2][contains(.,'" + state + "')]");
    WebElement element = visibilityWait(locator, MAX_PARAGRAPH_TIMEOUT_SEC);
    return element.isDisplayed();
  }

  protected String getParagraphStatus(final int paragraphNo) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]/span[2]");

    return manager.getWebDriver().findElement(locator).getText();
  }

  protected boolean waitForText(final String txt, final By locator) {
    try {
      WebElement element = visibilityWait(locator, MAX_BROWSER_TIMEOUT_SEC);
      return txt.equals(element.getText());
    } catch (TimeoutException e) {
      return false;
    }
  }

  protected WebElement pollingWait(final By locator, final long timeWait) {
    WebDriverWait wait = new WebDriverWait(manager.getWebDriver(),
        Duration.ofSeconds(timeWait));
    return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
  }

  protected WebElement visibilityWait(final By locator, final long timeWait) {
    WebDriverWait wait = new WebDriverWait(manager.getWebDriver(),
        Duration.ofSeconds(timeWait));
    return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
  }

  protected WebElement clickableWait(final By locator, final long timeWait) {
    WebDriverWait wait = new WebDriverWait(manager.getWebDriver(),
        Duration.ofSeconds(timeWait));
    return wait.until(ExpectedConditions.elementToBeClickable(locator));
  }

  protected void createNewNote() {
    clickAndWait(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
        " note')]"));

    WebDriverWait block =
      new WebDriverWait(manager.getWebDriver(), Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
    block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteCreateModal")));
    clickAndWait(By.id("createNoteButton"));
    block.until(ExpectedConditions.invisibilityOfElementLocated(By.id("createNoteButton")));
  }

  protected void deleteTestNotebook(final WebDriver driver) {
    WebDriverWait block = new WebDriverWait(driver, Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
    driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']"))
        .sendKeys(Keys.ENTER);
    block.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']")));
    driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'This note will be moved to trash')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    ZeppelinITUtils.sleep(100, false);
  }

  protected void deleteTrashNotebook(final WebDriver driver) {
    WebDriverWait block = new WebDriverWait(driver, Duration.ofSeconds(MAX_BROWSER_TIMEOUT_SEC));
    driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='removeNote(note.id)']"))
        .sendKeys(Keys.ENTER);
    block.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(".//*[@id='main']//button[@ng-click='removeNote(note.id)']")));
    driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'This cannot be undone. Are you sure?')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    ZeppelinITUtils.sleep(100, false);
  }

  protected void clickAndWait(final By locator) {
    WebElement element = clickableWait(locator, MAX_IMPLICIT_WAIT);
    try {
      element.click();
      ZeppelinITUtils.sleep(1000, false);
    } catch (ElementClickInterceptedException e) {
      // if the previous click did not happened mean the element is behind another clickable element
      Actions action = new Actions(manager.getWebDriver());
      action.moveToElement(element).click().build().perform();
      ZeppelinITUtils.sleep(1500, false);
    }
  }

  protected void handleException(String message, Exception e) throws Exception {
    LOGGER.error(message, e);
    File scrFile = ((TakesScreenshot) manager.getWebDriver()).getScreenshotAs(OutputType.FILE);
    LOGGER.error("ScreenShot::\ndata:image/png;base64," + new String(Base64.encodeBase64(FileUtils.readFileToByteArray(scrFile))));
    throw e;
  }

}
