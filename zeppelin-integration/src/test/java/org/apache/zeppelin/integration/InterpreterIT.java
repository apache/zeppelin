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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.Select;

class InterpreterIT extends AbstractZeppelinIT {

  @BeforeEach
  public void startUp() throws IOException {
    manager = new WebDriverManager();
  }

  @AfterEach
  public void tearDown() throws IOException {
    manager.close();
  }

  @Test
  void testShowDescriptionOnInterpreterCreate() throws Exception {
    try {
      // navigate to interpreter page
      // setting button
      clickAndWait(By.xpath("//button[@class='nav-btn dropdown-toggle ng-scope']"));

      // interpreter link
      clickAndWait(By.xpath("//a[@href='#/interpreter']"));

      // create button
      clickAndWait(By.xpath("//button[contains(., 'Create')]"));

      Select select = new Select(manager.getWebDriver()
        .findElement(By.xpath("//select[@ng-change='newInterpreterGroupChange()']")));
      select.selectByVisibleText("spark");

      assertEquals(
        "The name of spark application.",
        manager.getWebDriver()
          .findElement(
            By.xpath("//tr/td[contains(text(), 'spark.app.name')]/following-sibling::td[2]"))
          .getText(),
        "description of interpreter property is displayed");

    } catch (Exception e) {
      handleException("Exception in InterpreterIT while testShowDescriptionOnInterpreterCreate ", e);
    }
  }
}
