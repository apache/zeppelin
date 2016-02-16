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

import java.io.File;

import static org.apache.zeppelin.AbstractZeppelinIT.HelperKeys.*;
import static org.openqa.selenium.Keys.*;

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
    createNewNote();
    waitForParagraph(1, "READY");
  }

  @After
  public void tearDown() {
    if (!endToEndTestEnabled()) {
      return;
    }
    deleteTestNotebook(driver);
    driver.quit();
  }

  @Test
  public void testSpark() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));
      paragraph1Editor.sendKeys("sc.version");
      paragraph1Editor.sendKeys(SHIFT_ENTER);

      waitForParagraph(1, "FINISHED");
      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));
      Float sparkVersion = Float.parseFloat(paragraph1Result.getText().split("= ")[1].substring(0, 3));

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
          ENTER +

          "import java.net.URL" +
          ENTER +

          "import java.nio.charset.Charset" +
          ENTER +

          "val bankText = sc.parallelize" + OPEN_PARENTHESIS +
          "IOUtils.toString" + OPEN_PARENTHESIS + "new URL" + OPEN_PARENTHESIS
          + "\"https://s3.amazonaws.com/apache" + SUBTRACT + "zeppelin/tutorial/bank/bank." +
          "csv\"),Charset.forName" + OPEN_PARENTHESIS + "\"utf8\"))" +
          ".split" + OPEN_PARENTHESIS + "\"\\n\"))" +
          ENTER +

          "case class Bank" + OPEN_PARENTHESIS +
          "age: Integer, job: String, marital: String, education: String, balance: Integer)" +
          ENTER +
          ENTER +

          "val bank = bankText.map" + OPEN_PARENTHESIS + "s => s.split" +
          OPEN_PARENTHESIS + "\";\")).filter" + OPEN_PARENTHESIS +
          "s => s" + OPEN_PARENTHESIS + "0) " + EXCLAMATION +
          "= \"\\\"age\\\"\").map" + OPEN_PARENTHESIS +
          "s => Bank" + OPEN_PARENTHESIS + "s" + OPEN_PARENTHESIS +
          "0).toInt,s" + OPEN_PARENTHESIS + "1).replaceAll" +
          OPEN_PARENTHESIS + "\"\\\"\", \"\")," +
          "s" + OPEN_PARENTHESIS + "2).replaceAll" +
          OPEN_PARENTHESIS + "\"\\\"\", \"\")," +
          "s" + OPEN_PARENTHESIS + "3).replaceAll" +
          OPEN_PARENTHESIS + "\"\\\"\", \"\")," +
          "s" + OPEN_PARENTHESIS + "5).replaceAll" +
          OPEN_PARENTHESIS + "\"\\\"\", \"\").toInt" + ")" +
          ")" + (sparkVersion < 1.3f ? "" : ".toDF" + OPEN_PARENTHESIS + ")") +
          ENTER +

          "bank.registerTempTable" + OPEN_PARENTHESIS + "\"bank\")"
      );
      paragraph2Editor.sendKeys("" + END + BACK_SPACE + BACK_SPACE +
          BACK_SPACE + BACK_SPACE + BACK_SPACE + BACK_SPACE +
          BACK_SPACE + BACK_SPACE + BACK_SPACE + BACK_SPACE + BACK_SPACE);

      paragraph2Editor.sendKeys(SHIFT_ENTER);

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

    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testSpark", e);
    }
  }

  @Test
  public void testPySpark() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));

      paragraph1Editor.sendKeys(PERCENTAGE + "pyspark" + ENTER +
          "for x in range" + OPEN_PARENTHESIS + "0, 3):" + ENTER +
          "    print \"test loop " + PERCENTAGE + "d\" " +
          PERCENTAGE + " " + OPEN_PARENTHESIS + "x)" + ENTER);

      paragraph1Editor.sendKeys(SHIFT_ENTER);

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        WebElement paragraph1Result = driver.findElement(By.xpath(
            getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));
        if (paragraph1Result.getText().toString().contains("pyspark 1.1.1 is not supported")) {
          LOG.info("pyspark is not supported in spark version 1.1.x, nothing to worry.");
        } else {
          collector.checkThat("Paragraph result resulted in error ::",
              "ERROR", CoreMatchers.equalTo("FINISHED")
          );
          LOG.error("Paragraph got ERROR");
        }
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      if (!paragraph1Result.getText().toString().contains("pyspark 1.1.1 is not supported")) {
        collector.checkThat("Paragraph result is ::",
            paragraph1Result.getText().toString(), CoreMatchers.equalTo("test loop 0\ntest loop 1\ntest loop 2")
        );
      }

    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testPySpark", e);
    }
  }

  @Test
  public void testSqlSpark() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      WebElement paragraph1Editor = driver.findElement(By.xpath(getParagraphXPath(1) + "//textarea"));

      paragraph1Editor.sendKeys(PERCENTAGE + "sql" + ENTER +
          "select * from bank limit 1");

      paragraph1Editor.sendKeys(SHIFT_ENTER);

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
    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testSqlSpark", e);
    }
  }
}
