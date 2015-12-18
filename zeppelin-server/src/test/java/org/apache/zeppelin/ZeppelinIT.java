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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotVisibleException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

/**
 * Test Zeppelin with web browser.
 *
 * To test, ZeppelinServer should be running on port 8080
 * On OSX, you'll need firefox 42.0 installed, then you can run with
 *
 * PATH=~/Applications/Firefox.app/Contents/MacOS/:$PATH CI="" \
 *    mvn -Dtest=org.apache.zeppelin.ZeppelinIT -Denforcer.skip=true \
 *    test -pl zeppelin-server
 *
 */
public class ZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinIT.class);
  private static final long MAX_BROWSER_TIMEOUT_SEC = 30;
  private static final long MAX_PARAGRAPH_TIMEOUT_SEC = 60;
  private WebDriver driver;

  @Before
  public void startUp() {
    if (!endToEndTestEnabled()) {
      return;
    }
    driver = WebDriverManager.getWebDriver();
  }

  @After
  public void tearDown() {
    if (!endToEndTestEnabled()) {
      return;
    }

    driver.quit();
  }

  String getParagraphXPath(int paragraphNo) {
    return "//div[@ng-controller=\"ParagraphCtrl\"][" + paragraphNo +"]";
  }

  boolean waitForParagraph(final int paragraphNo, final String state) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]//span[1][contains(.,'" + state + "')]");
    WebElement element = pollingWait(locator, MAX_PARAGRAPH_TIMEOUT_SEC);
    return element.isDisplayed();
  }

  boolean waitForText(final String txt, final By locator) {
    try {
      WebElement element = pollingWait(locator, MAX_BROWSER_TIMEOUT_SEC);
      return txt.equals(element.getText());
    } catch (TimeoutException e) {
      return false;
    }
  }

  public WebElement pollingWait(final By locator, final long timeWait) {
    Wait<WebDriver> wait = new FluentWait<WebDriver>(driver)
            .withTimeout(timeWait, TimeUnit.SECONDS)
            .pollingEvery(1, TimeUnit.SECONDS)
            .ignoring(NoSuchElementException.class);

    return wait.until(new Function<WebDriver, WebElement>() {
        public WebElement apply(WebDriver driver) {
            return driver.findElement(locator);
        }
    });
  };

  boolean endToEndTestEnabled() {
    return null != System.getenv("CI");
  }

  @Test
  public void testAngularDisplay() throws InterruptedException{
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      /*
       * print angular template
       * %angular <div id='angularTestButton' ng-click='myVar=myVar+1'>BindingTest_{{myVar}}_</div>
       */
      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));
      paragraph1Editor.sendKeys("println" + Keys.chord(Keys.SHIFT, "9") + "\""
                  + Keys.chord(Keys.SHIFT, "5")
                  + "angular <div id='angularTestButton' "
                  + "ng" + Keys.chord(Keys.SUBTRACT) + "click='myVar=myVar+1'>"
                  + "BindingTest_{{myVar}}_</div>\")");
      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      waitForParagraph(1, "FINISHED");

      // check expected text
      waitForText("BindingTest__", By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind variable
       * z.angularBind("myVar", 1)
       */
      assertEquals(1, driver.findElements(By.xpath(getParagraphXPath(2) + "//textarea")).size());
      WebElement paragraph2Editor = driver.findElement(By.xpath(getParagraphXPath(2) + "//textarea"));
      paragraph2Editor.sendKeys("z.angularBind" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\", 1)");
      paragraph2Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      waitForParagraph(2, "FINISHED");

      // check expected text
      waitForText("BindingTest_1_", By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));


      /*
       * print variable
       * print("myVar="+z.angular("myVar"))
       */
      WebElement paragraph3Editor = driver.findElement(By.xpath(getParagraphXPath(3) + "//textarea"));
      paragraph3Editor.sendKeys(
          "print" + Keys.chord(Keys.SHIFT, "9") + "\"myVar=\"" + Keys.chord(Keys.ADD)
          + "z.angular" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\"))");
      paragraph3Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      waitForParagraph(3, "FINISHED");

      // check expected text
      waitForText("myVar=1", By.xpath(
          getParagraphXPath(3) + "//div[@ng-bind=\"paragraph.result.msg\"]"));

      /*
       * Click element
       */
      driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      waitForText("BindingTest_2_", By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Register watcher
       * z.angularWatch("myVar", (before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext) => {
       *   z.run(2, context)
       * }
       */
      WebElement paragraph4Editor = driver.findElement(By.xpath(getParagraphXPath(4) + "//textarea"));
      paragraph4Editor.sendKeys(
          "z.angularWatch" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\", "
          + Keys.chord(Keys.SHIFT, "9")
          + "before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext)"
          + Keys.EQUALS + ">{ z.run" +Keys.chord(Keys.SHIFT, "9") + "2, context)}");
      paragraph4Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      waitForParagraph(4, "FINISHED");


      /*
       * Click element, again and see watcher works
       */
      driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      waitForText("BindingTest_3_", By.xpath(
          getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));
      waitForParagraph(3, "FINISHED");

      // check expected text by watcher
      waitForText("myVar=3", By.xpath(
          getParagraphXPath(3) + "//div[@ng-bind=\"paragraph.result.msg\"]"));

      /*
       * Unbind
       * z.angularUnbind("myVar")
       */
      WebElement paragraph5Editor = driver.findElement(By.xpath(getParagraphXPath(5) + "//textarea"));
      paragraph5Editor.sendKeys(
          "z.angularUnbind" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\")");
      paragraph5Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      waitForParagraph(5, "FINISHED");

      // check expected text
      waitForText("BindingTest__",
          By.xpath(getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind again and see rebind works.
       */
      paragraph2Editor = driver.findElement(By.xpath(getParagraphXPath(2) + "//textarea"));
      paragraph2Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      waitForParagraph(2, "FINISHED");

      // check expected text
      waitForText("BindingTest_1_",
          By.xpath(getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      driver.findElement(By.xpath("//*[@id='main']/div//h3/span[1]/button[@tooltip='Remove the notebook']"))
          .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      ZeppelinITUtils.sleep(100, true);

      System.out.println("testCreateNotebook Test executed");
    } catch (ElementNotVisibleException e) {
      File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);

    }
  }

  private void createNewNote() {
    List<WebElement> notebookLinks = driver.findElements(By
        .xpath("//div[contains(@class, \"col-md-4\")]/div/ul/li"));
    List<String> notebookTitles = new LinkedList<String>();
    for (WebElement el : notebookLinks) {
      notebookTitles.add(el.getText());
    }

    WebElement createNoteLink = driver.findElement(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new note')]"));
    createNoteLink.click();

    WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    WebElement modal = block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteNameModal")));
    WebElement createNoteButton = modal.findElement(By.id("createNoteButton"));
    createNoteButton.click();

    try {
      Thread.sleep(500); // wait for notebook list updated
    } catch (InterruptedException e) {
    }
  }
}
