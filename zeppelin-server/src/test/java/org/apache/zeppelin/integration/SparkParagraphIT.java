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
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
      setTextOfParagraph(1, "sc.version");
      runParagraph(1);

      waitForParagraph(1, "FINISHED");

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
      setTextOfParagraph(2, "import org.apache.commons.io.IOUtils\\n" +
          "import java.net.URL\\n" +
          "import java.nio.charset.Charset\\n" +
          "val bankText = sc.parallelize(IOUtils.toString(new URL(\"https://s3.amazonaws.com/apache-zeppelin/tutorial/bank/bank.csv\"),Charset.forName(\"utf8\")).split(\"\\\\n\"))\\n" +
          "case class Bank(age: Integer, job: String, marital: String, education: String, balance: Integer)\\n" +
          "\\n" +
          "val bank = bankText.map(s => s.split(\";\")).filter(s => s(0) != \"\\\\\"age\\\\\"\").map(s => Bank(s(0).toInt,s(1).replaceAll(\"\\\\\"\", \"\"),s(2).replaceAll(\"\\\\\"\", \"\"),s(3).replaceAll(\"\\\\\"\", \"\"),s(5).replaceAll(\"\\\\\"\", \"\").toInt)).toDF()\\n" +
          "bank.registerTempTable(\"bank\")");
      runParagraph(2);

      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("2nd Paragraph from SparkParagraphIT of testSpark status:",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }

      WebElement paragraph2Result = driver.findElement(By.xpath(
          getParagraphXPath(2) + "//div[contains(@id,\"_text\")]"));

      collector.checkThat("2nd Paragraph from SparkParagraphIT of testSpark result: ",
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
      setTextOfParagraph(1, "%pyspark\\n" +
          "for x in range(0, 3):\\n" +
          "    print \"test loop %d\" % (x)");

      runParagraph(1);

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph from SparkParagraphIT of testPySpark status: ",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }

      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[contains(@id,\"_text\")]"));
      collector.checkThat("Paragraph from SparkParagraphIT of testPySpark result: ",
          paragraph1Result.getText().toString(), CoreMatchers.containsString("test loop 0\ntest loop 1\ntest loop 2")
      );

      // the last statement's evaluation result is printed
      setTextOfParagraph(2, "%pyspark\\n" +
          "sc.version\\n" +
          "1+1");
      runParagraph(2);
      try {
        waitForParagraph(2, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(2, "ERROR");
        collector.checkThat("Paragraph from SparkParagraphIT of testPySpark status: ",
                "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }
      WebElement paragraph2Result = driver.findElement(By.xpath(
              getParagraphXPath(2) + "//div[contains(@id,\"_text\")]"));
      collector.checkThat("Paragraph from SparkParagraphIT of testPySpark result: ",
          paragraph2Result.getText().toString(), CoreMatchers.equalTo("2")
      );

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
      setTextOfParagraph(1,"%sql\\n" +
          "select * from bank limit 1");
      runParagraph(1);

      try {
        waitForParagraph(1, "FINISHED");
      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("Paragraph from SparkParagraphIT of testSqlSpark status: ",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }

      // Age, Job, Marital, Education, Balance
      List<WebElement> tableHeaders = driver.findElements(By.cssSelector("span.ui-grid-header-cell-label"));
      String headerNames = "";

      for(WebElement header : tableHeaders) {
        headerNames += header.getText().toString() + "|";
      }

      collector.checkThat("Paragraph from SparkParagraphIT of testSqlSpark result: ",
          headerNames, CoreMatchers.equalTo("age|job|marital|education|balance|"));
    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testSqlSpark", e);
    }
  }

  @Test
  public void testDep() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      // restart spark interpreter before running %dep
      clickAndWait(By.xpath("//span[@uib-tooltip='Interpreter binding']"));
      clickAndWait(By.xpath("//div[font[contains(text(), 'spark')]]/preceding-sibling::a[@uib-tooltip='Restart']"));
      clickAndWait(By.xpath("//button[contains(.,'OK')]"));

      setTextOfParagraph(1,"%dep z.load(\"org.apache.commons:commons-csv:1.1\")");
      runParagraph(1);

      try {
        waitForParagraph(1, "FINISHED");
        WebElement paragraph1Result = driver.findElement(By.xpath(getParagraphXPath(1) +
            "//div[contains(@id,'_text')]"));
        collector.checkThat("Paragraph from SparkParagraphIT of testSqlSpark result: ",
            paragraph1Result.getText(), CoreMatchers.containsString("res0: org.apache.zeppelin.dep.Dependency = org.apache.zeppelin.dep.Dependency"));

        setTextOfParagraph(2, "import org.apache.commons.csv.CSVFormat");
        runParagraph(2);

        try {
          waitForParagraph(2, "FINISHED");
          WebElement paragraph2Result = driver.findElement(By.xpath(getParagraphXPath(2) +
              "//div[contains(@id,'_text')]"));
          collector.checkThat("Paragraph from SparkParagraphIT of testSqlSpark result: ",
              paragraph2Result.getText(), CoreMatchers.equalTo("import org.apache.commons.csv.CSVFormat"));

        } catch (TimeoutException e) {
          waitForParagraph(2, "ERROR");
          collector.checkThat("Second paragraph from SparkParagraphIT of testDep status: ",
              "ERROR", CoreMatchers.equalTo("FINISHED")
          );
        }

      } catch (TimeoutException e) {
        waitForParagraph(1, "ERROR");
        collector.checkThat("First paragraph from SparkParagraphIT of testDep status: ",
            "ERROR", CoreMatchers.equalTo("FINISHED")
        );
      }
    } catch (Exception e) {
      handleException("Exception in SparkParagraphIT while testDep", e);
    }
  }
}
