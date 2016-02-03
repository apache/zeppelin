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


import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TestSparkParagraph extends AbstractZeppelinIT {

  private static final Logger LOG = LoggerFactory.getLogger(TestSparkParagraph.class);

  @Rule
  public ErrorCollector collector = new ErrorCollector();

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

  @Test
  public void testSpark() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");

      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));

      paragraph1Editor.sendKeys("import org.apache.commons.io.IOUtils"
          + Keys.ENTER +

          "import java.net.URL" + Keys.ENTER +

          "import java.nio.charset.Charset" + Keys.ENTER +

          "val bankText = sc.parallelize" + Keys.chord(Keys.SHIFT, "9") +
          "IOUtils.toString" + Keys.chord(Keys.SHIFT, "9") + "new URL" + Keys.chord(Keys.SHIFT, "9")
          + "\"https://s3.amazonaws.com/apache" + Keys.SUBTRACT + "zeppelin/tutorial/bank/bank." +
          "csv\"),Charset.forName" + Keys.chord(Keys.SHIFT, "9") + "\"utf8\"))" +
          ".split" + Keys.chord(Keys.SHIFT, "9") + "\"\\n\"))" + Keys.ENTER +

          "case class Bank" + Keys.chord(Keys.SHIFT, "9") +
          "age: Integer, job: String, marital: String, education: String, balance: Integer)" +
          Keys.ENTER +
          Keys.ENTER +

          "val bank = bankText.map" + Keys.chord(Keys.SHIFT, "9") + "s => s.split" +
          Keys.chord(Keys.SHIFT, "9") + "\";\")).filter" + Keys.chord(Keys.SHIFT, "9") +
          "s => s" + Keys.chord(Keys.SHIFT, "9") + "0) " + Keys.chord(Keys.SHIFT, "1") +
          "= \"\\\"age\\\"\").map" + Keys.chord(Keys.SHIFT, "9") +
          "s => Bank" + Keys.chord(Keys.SHIFT, "9") + "s" + Keys.chord(Keys.SHIFT, "9") +
          "0).toInt,s" + Keys.chord(Keys.SHIFT, "9") + "1).replaceAll" +
          Keys.chord(Keys.SHIFT, "9") + "\"\\\"\", \"\")," +
          "s" + Keys.chord(Keys.SHIFT, "9") + "2).replaceAll" +
          Keys.chord(Keys.SHIFT, "9") + "\"\\\"\", \"\")," +
          "s" + Keys.chord(Keys.SHIFT, "9") + "3).replaceAll" +
          Keys.chord(Keys.SHIFT, "9") + "\"\\\"\", \"\")," +
          "s" + Keys.chord(Keys.SHIFT, "9") + "5).replaceAll" +
          Keys.chord(Keys.SHIFT, "9") + "\"\\\"\", \"\").toInt" + ")" +
          ").toDF" + Keys.chord(Keys.SHIFT, "9") + ")" + Keys.ENTER +

          "bank.registerTempTable" + Keys.chord(Keys.SHIFT, "9") + "\"bank\")"
      );
      paragraph1Editor.sendKeys("" + Keys.END + Keys.BACK_SPACE + Keys.BACK_SPACE +
          Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE +
          Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE);

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph result resulted in error ::",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph result is ::",
          paragraph1Result.getText().toString(), CoreMatchers.containsString(
              "import org.apache.commons.io.IOUtils"
          )
      );

      driver.findElement(By.xpath(
          "//*[@id='main']/div//h3/span[1]/button[@tooltip='Remove the notebook']")
      ).sendKeys(Keys.ENTER);
      sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      sleep(100, true);


    } catch (ElementNotVisibleException e) {
      ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

    }
  }

  @Test
  public void testPySpark() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");

      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, "5") + "pyspark" + Keys.ENTER +
          "for x in range" + Keys.chord(Keys.SHIFT, "9") + "0, 3):" + Keys.ENTER +
          "    print \"test loop " + Keys.chord(Keys.SHIFT, "5") + "d\" " +
          Keys.chord(Keys.SHIFT, "5") + " " + Keys.chord(Keys.SHIFT, "9") + "x)" + Keys.ENTER);

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph result resulted in error ::",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph result is ::",
          paragraph1Result.getText().toString(), CoreMatchers.equalTo("test loop 0\ntest loop 1\ntest loop 2")
      );

      driver.findElement(By.xpath("//*[@id='main']/div//h3/span[1]/button[@tooltip='Remove the notebook']"))
          .sendKeys(Keys.ENTER);
      sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      sleep(100, true);


    } catch (ElementNotVisibleException e) {
      ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

    }
  }

  @Test
  public void testSqlSpark() throws InterruptedException {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      waitForParagraph(1, "READY");

      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, "5") + "sql" + Keys.ENTER +
          "select * from bank limit 1");

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph result resulted in error ::",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph result is ::",
          paragraph1Result.getText().toString(), CoreMatchers.equalTo("age job marital education balance\n" +
              "30 unemployed married primary 1,787")
      );

      driver.findElement(By.xpath("//*[@id='main']/div//h3/span[1]/button[@tooltip='Remove the notebook']"))
          .sendKeys(Keys.ENTER);
      sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      sleep(100, true);
    } catch (ElementNotVisibleException e) {
      ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

    }
  }
}