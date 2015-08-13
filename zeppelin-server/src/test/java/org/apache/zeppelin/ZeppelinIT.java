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

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Test Zeppelin with web brower.
 * 
 * To test, ZeppelinServer should be running on port 8080
 * On OSX, you'll need firefox 31.0 installed. 
 *
 */
public class ZeppelinIT {
  private WebDriver driver;

  private WebDriver getWebDriver() {
    WebDriver driver = null;

    if (driver == null) {
      try {
        FirefoxBinary ffox = new FirefoxBinary();
        if ("true".equals(System.getenv("TRAVIS"))) {
          ffox.setEnvironmentProperty("DISPLAY", ":99"); // xvfb is supposed to
                                                         // run with DISPLAY 99
        }
        FirefoxProfile profile = new FirefoxProfile();
        driver = new FirefoxDriver(ffox, profile);
      } catch (Exception e) {
      }
    }

    if (driver == null) {
      try {
        driver = new ChromeDriver();
      } catch (Exception e) {
      }
    }

    if (driver == null) {
      try {
        driver = new SafariDriver();
      } catch (Exception e) {
      }
    }

    String url;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    } else {
      url = "http://localhost:8080";
    }

    long start = System.currentTimeMillis();
    boolean loaded = false;
    driver.get(url);

    while (System.currentTimeMillis() - start < 60 * 1000) {
      // wait for page load
      try {
        (new WebDriverWait(driver, 5)).until(new ExpectedCondition<Boolean>() {
          @Override
          public Boolean apply(WebDriver d) {
            return d.findElement(By.partialLinkText("Create new note"))
                .isDisplayed();
          }
        });
        loaded = true;
        break;
      } catch (TimeoutException e) {
        driver.navigate().to(url);
      }
    }

    if (loaded == false) {
      fail();
    }

    return driver;
  }

  @Before
  public void startUp() {
    if (!endToEndTestEnabled()) {
      return;
    }

    driver = getWebDriver();
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

  void waitForParagraph(final int paragraphNo, final String state) {
    (new WebDriverWait(driver, 60)).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        return driver.findElement(By.xpath(getParagraphXPath(paragraphNo)
                + "//div[@class=\"control\"]//span[1][text()=\" " + state + " \"]"))
            .isDisplayed();
      };
    });
  }

  boolean endToEndTestEnabled() {
    return null != System.getenv("CI");
  }

  boolean waitForText(final String txt, final By by) {
    try {
      new WebDriverWait(driver, 5).until(new ExpectedCondition<Boolean>() {
        @Override
        public Boolean apply(WebDriver d) {
          return txt.equals(driver.findElement(by).getText());
        }
      });
      return true;
    } catch (TimeoutException e) {
      return false;
    }
  }

	@Test
  public void testAngularDisplay() throws InterruptedException{
    if (!endToEndTestEnabled()) {
      return;
    }

	  String noteName = createNewNoteAndGetName();
	  driver.findElement(By.partialLinkText(noteName)).click();

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

    System.out.println("testCreateNotebook Test executed");
  }

  private String createNewNoteAndGetName() {
    List<WebElement> notebookLinks = driver.findElements(By
        .xpath("//div[contains(@class, \"col-md-4\")]/div/ul/li"));    
    List<String> notebookTitles = new LinkedList<String>();
    for (WebElement el : notebookLinks) {
      notebookTitles.add(el.getText());
    }
    
	WebElement createNoteLink = driver.findElement(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a"));
	createNoteLink.click();

	WebDriverWait block = new WebDriverWait(driver, 10);
	WebElement modal = block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteNameModal")));
	WebElement createNoteButton = modal.findElement(By.id("createNoteButton"));
	createNoteButton.click();

    try {
      Thread.sleep(500); // wait for notebook list updated
    } catch (InterruptedException e) {
    } 

    List<WebElement> notebookLinksAfterCreate = driver.findElements(By
        .xpath("//div[contains(@class, \"col-md-4\")]/div/ul/li"));

    Iterator<WebElement> it = notebookLinksAfterCreate.iterator();
    while (it.hasNext()) {
      WebElement newEl = it.next();
      if (notebookTitles.contains(newEl.getText())) {
        
        it.remove();
      }
    }

    assertEquals(1, notebookLinksAfterCreate.size());
    return notebookLinksAfterCreate.get(0).getText();
  }
}
