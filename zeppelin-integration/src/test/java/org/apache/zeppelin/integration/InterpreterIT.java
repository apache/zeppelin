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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterpreterIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterIT.class);

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  @Before
  public void startUp() {
    driver = WebDriverManager.getWebDriver();
  }

  @After
  public void tearDown() {
    driver.quit();
  }

  @Test
  public void testShowDescriptionOnInterpreterCreate() throws Exception {
    try {
      // navigate to interpreter page
      WebElement settingButton = driver.findElement(By.xpath("//button[@class='nav-btn dropdown-toggle ng-scope']"));
      settingButton.click();
      WebElement interpreterLink = driver.findElement(By.xpath("//a[@href='#/interpreter']"));
      interpreterLink.click();

      WebElement createButton = driver.findElement(By.xpath("//button[contains(., 'Create')]"));
      createButton.click();

      Select select = new Select(driver.findElement(By.xpath("//select[@ng-change='newInterpreterGroupChange()']")));
      select.selectByVisibleText("spark");

      collector.checkThat("description of interpreter property is displayed",
          driver.findElement(By.xpath("//tr/td[contains(text(), 'spark.app.name')]/following-sibling::td[3]")).getText(),
          CoreMatchers.equalTo("The name of spark application."));

    } catch (Exception e) {
      handleException("Exception in InterpreterIT while testShowDescriptionOnInterpreterCreate ", e);
    }
  }
}
