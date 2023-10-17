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
import org.apache.zeppelin.ZeppelinITUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.zeppelin.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;

class SparkParagraphIT extends AbstractZeppelinIT {

  @BeforeEach
  public void startUp() throws IOException {
    manager = new WebDriverManager();
    createNewNote();
    waitForParagraph(1, "READY");
  }

  @AfterEach
  public void tearDown() throws IOException {
    deleteTestNotebook(manager.getWebDriver());
    manager.close();
  }

  @Test
  void testSpark() throws Exception {
    try {
      setTextOfParagraph(1, "sc.version");
      runParagraph(1);

      waitForParagraph(1, "FINISHED");

      /*
      equivalent of
      import org.apache.commons.io.IOUtils
      import java.net.URL
      import java.nio.charset.Charset
      val bankText = sc.parallelize(IOUtils.toString(new URL("https://raw.githubusercontent.com/apache/zeppelin/master/testing/resources/bank.csv"),Charset.forName("utf8")).split("\n"))
      case class Bank(age: Integer, job: String, marital: String, education: String, balance: Integer)

      val bank = bankText.map(s => s.split(";")).filter(s => s(0) != "\"age\"").map(s => Bank(s(0).toInt,s(1).replaceAll("\"", ""),s(2).replaceAll("\"", ""),s(3).replaceAll("\"", ""),s(5).replaceAll("\"", "").toInt)).toDF()
      bank.registerTempTable("bank")
       */
      setTextOfParagraph(2, "import org.apache.commons.io.IOUtils\\n" +
          "import java.net.URL\\n" +
          "import java.nio.charset.Charset\\n" +
          "val bankText = sc.parallelize(IOUtils.toString(new URL(\"https://raw.githubusercontent.com/apache/zeppelin/master/testing/resources/bank.csv\"),Charset.forName(\"utf8\")).split(\"\\\\n\"))\\n" +
          "case class Bank(age: Integer, job: String, marital: String, education: String, balance: Integer)\\n" +
          "\\n" +
          "val bank = bankText.map(s => s.split(\";\")).filter(s => s(0) != \"\\\\\"age\\\\\"\").map(s => Bank(s(0).toInt,s(1).replaceAll(\"\\\\\"\", \"\"),s(2).replaceAll(\"\\\\\"\", \"\"),s(3).replaceAll(\"\\\\\"\", \"\"),s(5).replaceAll(\"\\\\\"\", \"\").toInt)).toDF()\\n" +
          "bank.registerTempTable(\"bank\")");
      runParagraph(2);

      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        fail("2nd Paragraph from SparkParagraphIT of testSpark status:");
      }

      WebElement paragraph2Result = manager.getWebDriver().findElement(By.xpath(
          getParagraphXPath(2) + "//div[contains(@id,\"_text\")]"));

      assertTrue(paragraph2Result.getText().contains("import org.apache.commons.io.IOUtils"),
          paragraph2Result.getText());
    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testSpark", e);
    }
  }

  @Test
  void testPySpark() throws Exception {
    try {
      setTextOfParagraph(1, "%pyspark\\n" +
          "for x in range(0, 3):\\n" +
          "    print(\"test loop %d\" % (x))");

      runParagraph(1);

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        fail("Paragraph from SparkParagraphIT of testPySpark status: ERROR");
      }

      WebElement paragraph1Result = manager.getWebDriver().findElement(By.xpath(
          getParagraphXPath(1) + "//div[contains(@id,\"_text\")]"));
      assertTrue(paragraph1Result.getText().contains("test loop 0\ntest loop 1\ntest loop 2"),
          paragraph1Result.getText());

      // the last statement's evaluation result is printed
      setTextOfParagraph(2, "%pyspark\\n" +
          "sc.version\\n" +
          "1+1");
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        fail("Paragraph from SparkParagraphIT of testPySpark status: ERROR");
      }
      WebElement paragraph2Result = manager.getWebDriver().findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@id,\"_text\")]"));
      assertEquals("2", paragraph2Result.getText(), "Paragraph from SparkParagraphIT of testPySpark result: " + paragraph2Result.getText());

    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testPySpark", e);
    }
  }

  @Test
  void testCancelPyspark() throws Exception {
    try {
      setTextOfParagraph(1, "%pyspark\\nimport time\\nfor i in range(0, 30):\\n\\ttime.sleep(1)");
      manager.getWebDriver()
          .findElement(By.xpath(getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
      manager.getWebDriver()
          .findElement(
              By.xpath(getParagraphXPath(1) + "//ul/li/a[@ng-click=\"insertNew('below')\"]"))
              .click();
      waitForParagraph(2, "READY");
      setTextOfParagraph(2, "%pyspark\\nprint(\"Hello World!\")");


      manager.getWebDriver()
          .findElement(
              By.xpath(".//*[@id='main']//button[contains(@ng-click, 'runAllParagraphs')]"))
          .sendKeys(Keys.ENTER);
      ZeppelinITUtils.sleep(1000, false);
      manager.getWebDriver()
          .findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
              "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      waitForParagraph(1, "RUNNING");

      ZeppelinITUtils.sleep(2000, false);
      cancelParagraph(1);
      waitForParagraph(1, "ABORT");

      assertEquals("ABORT", getParagraphStatus(1),
          "First paragraph status is " + getParagraphStatus(1));
      assertEquals("READY", getParagraphStatus(2),
          "Second paragraph status is " + getParagraphStatus(2));

      manager.getWebDriver().navigate().refresh();
      ZeppelinITUtils.sleep(3000, false);

    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testCancelPyspark", e);
    }
  }

  @Test
  void testSqlSpark() throws Exception {
    try {
      setTextOfParagraph(1,"%sql\\n" +
          "select * from bank limit 1");
      runParagraph(1);

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        fail("Paragraph from SparkParagraphIT of testSqlSpark status: ERROR");
      }

      // Age, Job, Marital, Education, Balance
      List<WebElement> tableHeaders =
          manager.getWebDriver().findElements(By.cssSelector("span.ui-grid-header-cell-label"));
      String headerNames = "";

      for(WebElement header : tableHeaders) {
        headerNames += header.getText().toString() + "|";
      }

      assertEquals("age|job|marital|education|balance|", headerNames,
          "Paragraph from SparkParagraphIT of testSqlSpark result: " + headerNames);
    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testSqlSpark", e);
    }
  }

//  @Test
  public void testDep() throws Exception {
    try {
      // restart spark interpreter before running %dep
      clickAndWait(By.xpath("//span[@uib-tooltip='Interpreter binding']"));
      clickAndWait(By.xpath("//div[font[contains(text(), 'spark')]]/preceding-sibling::a[@uib-tooltip='Restart']"));
      clickAndWait(By.xpath("//button[contains(.,'OK')]"));

      setTextOfParagraph(1,"%dep z.load(\"org.apache.commons:commons-csv:1.1\")");
      runParagraph(1);

      try {
        waitForParagraph(1, "FINISHED");
        WebElement paragraph1Result =
            manager.getWebDriver().findElement(By.xpath(getParagraphXPath(1) +
            "//div[contains(@id,'_text')]"));
        assertTrue(
            paragraph1Result.getText().contains(
                "res0: org.apache.zeppelin.dep.Dependency = org.apache.zeppelin.dep.Dependency"),
            paragraph1Result.getText());
        setTextOfParagraph(2, "import org.apache.commons.csv.CSVFormat");
        runParagraph(2);

        try {
          waitForParagraph(2, "FINISHED");
          WebElement paragraph2Result =
              manager.getWebDriver().findElement(By.xpath(getParagraphXPath(2) +
              "//div[contains(@id,'_text')]"));
          assertEquals("import org.apache.commons.csv.CSVFormat", paragraph2Result.getText(),
              "Paragraph from SparkParagraphIT of testSqlSpark result: "
                  + paragraph2Result.getText());
        } catch (TimeoutException e) {
          waitForParagraph(2, "ERROR");
          fail("Second paragraph from SparkParagraphIT of testDep status: ERROR");
        }

      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        fail("First paragraph from SparkParagraphIT of testDep status: ERROR");
      }
    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testDep", e);
    }
  }
}
