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

import com.google.common.base.Function;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

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
  private WebDriver driver;

  @Before
  public void startUp() {
    if (!TestUtils.endToEndTestEnabled()) {
      return;
    }
    driver = WebDriverManager.getWebDriver();
  }

  @After
  public void tearDown() {
    if (!TestUtils.endToEndTestEnabled()) {
      return;
    }

    driver.quit();
  }

  @Test
  public void testAngularDisplay() throws InterruptedException{
    if (!TestUtils.endToEndTestEnabled()) {
      return;
    }
    try {
      TestUtils.createNewNote(driver);

      // wait for first paragraph's " READY " status text
      TestUtils.waitForParagraph(1, "READY", driver);

      /*
       * print angular template
       * %angular <div id='angularTestButton' ng-click='myVar=myVar+1'>BindingTest_{{myVar}}_</div>
       */
      WebElement paragraph1Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(1) + "//textarea"));
      paragraph1Editor.sendKeys("println" + Keys.chord(Keys.SHIFT, "9") + "\""
                  + Keys.chord(Keys.SHIFT, "5")
                  + "angular <div id='angularTestButton' "
                  + "ng" + Keys.chord(Keys.SUBTRACT) + "click='myVar=myVar+1'>"
                  + "BindingTest_{{myVar}}_</div>\")");
      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      TestUtils.waitForParagraph(1, "FINISHED", driver);

      // check expected text
      TestUtils.waitForText("BindingTest__", By.xpath(
              TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"), driver);

      /*
       * Bind variable
       * z.angularBind("myVar", 1)
       */
      assertEquals(1, driver.findElements(By.xpath(TestUtils.getParagraphXPath(2) + "//textarea")).size());
      WebElement paragraph2Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(2) + "//textarea"));
      paragraph2Editor.sendKeys("z.angularBind" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\", 1)");
      paragraph2Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      TestUtils.waitForParagraph(2, "FINISHED", driver);

      // check expected text
      TestUtils.waitForText("BindingTest_1_", By.xpath(
              TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"), driver);


      /*
       * print variable
       * print("myVar="+z.angular("myVar"))
       */
      WebElement paragraph3Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(3) + "//textarea"));
      paragraph3Editor.sendKeys(
          "print" + Keys.chord(Keys.SHIFT, "9") + "\"myVar=\"" + Keys.chord(Keys.ADD)
          + "z.angular" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\"))");
      paragraph3Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      TestUtils.waitForParagraph(3, "FINISHED", driver);

      // check expected text
      TestUtils.waitForText("myVar=1", By.xpath(
              TestUtils.getParagraphXPath(3) + "//div[@ng-bind=\"paragraph.result.msg\"]"), driver);

      /*
       * Click element
       */
      driver.findElement(By.xpath(
              TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      TestUtils.waitForText("BindingTest_2_", By.xpath(
              TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"), driver);

      /*
       * Register watcher
       * z.angularWatch("myVar", (before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext) => {
       *   z.run(2, context)
       * }
       */
      WebElement paragraph4Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(4) + "//textarea"));
      paragraph4Editor.sendKeys(
          "z.angularWatch" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\", "
          + Keys.chord(Keys.SHIFT, "9")
          + "before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext)"
          + Keys.EQUALS + ">{ z.run" +Keys.chord(Keys.SHIFT, "9") + "2, context)}");
      paragraph4Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      TestUtils.waitForParagraph(4, "FINISHED", driver);


      /*
       * Click element, again and see watcher works
       */
      driver.findElement(By.xpath(
              TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      TestUtils.waitForText("BindingTest_3_", By.xpath(
              TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"), driver);
      TestUtils.waitForParagraph(3, "FINISHED", driver);

      // check expected text by watcher
      TestUtils.waitForText("myVar=3", By.xpath(
              TestUtils.getParagraphXPath(3) + "//div[@ng-bind=\"paragraph.result.msg\"]"), driver);

      /*
       * Unbind
       * z.angularUnbind("myVar")
       */
      WebElement paragraph5Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(5) + "//textarea"));
      paragraph5Editor.sendKeys(
          "z.angularUnbind" + Keys.chord(Keys.SHIFT, "9") + "\"myVar\")");
      paragraph5Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      TestUtils.waitForParagraph(5, "FINISHED", driver);

      // check expected text
      TestUtils.waitForText("BindingTest__",
          By.xpath(TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"), driver);

      /*
       * Bind again and see rebind works.
       */
      paragraph2Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(2) + "//textarea"));
      paragraph2Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));
      TestUtils.waitForParagraph(2, "FINISHED", driver);

      // check expected text
      TestUtils.waitForText("BindingTest_1_",
          By.xpath(TestUtils.getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"), driver);

      driver.findElement(By.xpath("//*[@id='main']/div//h3/span[1]/button[@tooltip='Remove the notebook']"))
          .sendKeys(Keys.ENTER);
      TestUtils.sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      TestUtils.sleep(100, true);

      System.out.println("testCreateNotebook Test executed");
    } catch (ElementNotVisibleException e) {
      LOG.error("Exception in ZeppelinIT while testAngularDisplay ", e);
      File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);

    }
  }
}
