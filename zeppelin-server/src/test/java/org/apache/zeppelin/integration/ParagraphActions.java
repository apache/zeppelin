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


import org.apache.zeppelin.TestUtils;
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

public class ParagraphActions {
    private static final Logger LOG = LoggerFactory.getLogger(ParagraphActions.class);
    private WebDriver driver;

    @Rule
    public ErrorCollector collector = new ErrorCollector();

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
    public void testDisableParagraphRunButton() throws InterruptedException {
        if (!TestUtils.endToEndTestEnabled()) {
            return;
        }
        try {
            TestUtils.createNewNote(driver);

            TestUtils.waitForParagraph(1, "READY", driver);
            WebElement paragraph1Editor = driver.findElement(By.xpath(TestUtils.getParagraphXPath(1) + "//textarea"));
            paragraph1Editor.sendKeys("println" + Keys.chord(Keys.SHIFT, "9") + "\""
                    + "abcd\")");

            driver.findElement(By.xpath(TestUtils.getParagraphXPath(1) + "//span[@class='icon-settings']")).click();
            driver.findElement(By.xpath(TestUtils.getParagraphXPath(1) + "//ul/li/a[@ng-click='toggleEnableDisable()']")).click();
            collector.checkThat("The play button class was ",
                    driver.findElement(By.xpath(TestUtils.getParagraphXPath(1) + "//span[@class='icon-control-play']")).isDisplayed(), CoreMatchers.equalTo(false)
            );

            driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='runNote()']")).sendKeys(Keys.ENTER);
            TestUtils.sleep(1000, true);
            driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'Run all paragraphs?')]" +
                    "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
            TestUtils.sleep(2000, false);

            collector.checkThat("Paragraph status is ",
                    TestUtils.getParagraphStatus(1, driver), CoreMatchers.equalTo("READY")
            );


            TestUtils.deleteTestNotebook(driver);

        } catch (ElementNotVisibleException e) {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        }

    }
}