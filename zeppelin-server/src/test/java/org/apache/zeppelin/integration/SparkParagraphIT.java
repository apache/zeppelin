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

public class SparkParagraphIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(SparkParagraphIT.class);


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
      paragraph1Editor.sendKeys("sc.version");
      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      waitForParagraph(1, "FINISHED");
      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));
      Float sparkVersion = Float.parseFloat(paragraph1Result.getText().split("= ")[1].substring(0,3));

      WebElement paragraph2Editor = driver.findElement(By.xpath(getParagraphXPath(2) + "//textarea"));


      /*
      equivalent of
      import org.apache.commons.io.IOUtils
      import java.net.URL
      import java.nio.charset.Charset
      val bankText = sc.parallelize(IOUtils.toString(new URL("https://s3.amazonaws.com/apache-zeppelin/tutorial/bank/bank.csv"),Charset.forName("utf8")).split("\n"))
      case class Bank(age: Integer, job: String, marital: String, education: String, balance: Integer)

      val bank = bankText.map(s => s.split(";")).filter(s => s(0) != "\"age\"").map(s => Bank(s(0).toInt,s(1).replaceAll("\"", ""),s(2).replaceAll("\"", ""),s(3).replaceAll("\"", ""),s(5).replaceAll("\"", "").toInt)).toDF()
      bank.registerTempTable("bank")
       */
      paragraph2Editor.sendKeys("import org.apache.commons.io.IOUtils" +
          Keys.ENTER +

          "import java.net.URL" +
          Keys.ENTER +

          "import java.nio.charset.Charset" +
          Keys.ENTER +

          "val bankText = sc.parallelize" + HelperKeys.OPENING_PARENTHESIS +
          "IOUtils.toString" + HelperKeys.OPENING_PARENTHESIS + "new URL" + HelperKeys.OPENING_PARENTHESIS
          + "\"https://s3.amazonaws.com/apache" + Keys.SUBTRACT + "zeppelin/tutorial/bank/bank." +
          "csv\"),Charset.forName" + HelperKeys.OPENING_PARENTHESIS + "\"utf8\"))" +
          ".split" + HelperKeys.OPENING_PARENTHESIS + "\"\\n\"))" +
          Keys.ENTER +

          "case class Bank" + HelperKeys.OPENING_PARENTHESIS +
          "age: Integer, job: String, marital: String, education: String, balance: Integer)" +
          Keys.ENTER +
          Keys.ENTER +

          "val bank = bankText.map" + HelperKeys.OPENING_PARENTHESIS + "s => s.split" +
          HelperKeys.OPENING_PARENTHESIS + "\";\")).filter" + HelperKeys.OPENING_PARENTHESIS +
          "s => s" + HelperKeys.OPENING_PARENTHESIS + "0) " + HelperKeys.EXCLAMATION +
          "= \"\\\"age\\\"\").map" + HelperKeys.OPENING_PARENTHESIS +
          "s => Bank" + HelperKeys.OPENING_PARENTHESIS + "s" + HelperKeys.OPENING_PARENTHESIS +
          "0).toInt,s" + HelperKeys.OPENING_PARENTHESIS + "1).replaceAll" +
          HelperKeys.OPENING_PARENTHESIS + "\"\\\"\", \"\")," +
          "s" + HelperKeys.OPENING_PARENTHESIS + "2).replaceAll" +
          HelperKeys.OPENING_PARENTHESIS + "\"\\\"\", \"\")," +
          "s" + HelperKeys.OPENING_PARENTHESIS + "3).replaceAll" +
          HelperKeys.OPENING_PARENTHESIS + "\"\\\"\", \"\")," +
          "s" + HelperKeys.OPENING_PARENTHESIS + "5).replaceAll" +
          HelperKeys.OPENING_PARENTHESIS + "\"\\\"\", \"\").toInt" + ")" +
          ")" + (sparkVersion < 1.3f ? "" : ".toDF" + HelperKeys.OPENING_PARENTHESIS + ")") +
          Keys.ENTER +

          "bank.registerTempTable" + HelperKeys.OPENING_PARENTHESIS + "\"bank\")"
      );
      paragraph2Editor.sendKeys("" + Keys.END + Keys.BACK_SPACE + Keys.BACK_SPACE +
          Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE +
          Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE + Keys.BACK_SPACE);

      paragraph2Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Paragraph result resulted in error ::",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
        LOG.error("Paragraph got ERROR");
      }

      WebElement paragraph2Result = driver.findElement(By.xpath(
          getParagraphXPath(2) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph result is ::",
          paragraph2Result.getText().toString(), CoreMatchers.containsString(
              "import org.apache.commons.io.IOUtils"
          )
      );

      deleteTestNotebook(driver);

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

      paragraph1Editor.sendKeys(HelperKeys.PERCENTAGE + "pyspark" + Keys.ENTER +
          "for x in range" + HelperKeys.OPENING_PARENTHESIS + "0, 3):" + Keys.ENTER +
          "    print \"test loop " + HelperKeys.PERCENTAGE + "d\" " +
          HelperKeys.PERCENTAGE + " " + HelperKeys.OPENING_PARENTHESIS + "x)" + Keys.ENTER);

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph result resulted in error ::",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
        LOG.error("Paragraph got ERROR");
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph result is ::",
          paragraph1Result.getText().toString(), CoreMatchers.equalTo("test loop 0\ntest loop 1\ntest loop 2")
      );

      deleteTestNotebook(driver);

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

      paragraph1Editor.sendKeys(HelperKeys.PERCENTAGE + "sql" + Keys.ENTER +
          "select * from bank limit 1");

      paragraph1Editor.sendKeys(Keys.chord(Keys.SHIFT, Keys.ENTER));

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph result resulted in error ::",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
        LOG.error("Paragraph got ERROR");
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph result is ::",
          paragraph1Result.getText().toString(), CoreMatchers.equalTo("age job marital education balance\n" +
              "30 unemployed married primary 1,787")
      );

      deleteTestNotebook(driver);

    } catch (ElementNotVisibleException e) {
      ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

    }
  }
}