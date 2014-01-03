package com.nflabs.zeppelin;

import static org.junit.Assert.fail;

import java.io.File;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.webautomation.ScreenCaptureHtmlUnitDriver;

public class ZeppelinIT  {
    @Test
    public void runSimpleQueryInNewSession() {
        // Notice that the remainder of the code relies on the interface, 
        // not the implementation.
        WebDriver driver = new ScreenCaptureHtmlUnitDriver(); //HtmlUnitDriver();

        try {
            // go to zeppelin
            driver.get("http://localhost:8080");
            // driver.navigate().to("http://www.google.com");

            // Find the text input element by its text
            WebElement start = driver.findElement(By.partialLinkText("Start"));
            start.click();

            // Check the title of the page
            System.out.println("Page title is: " + driver.getTitle());

            // Google's search is rendered dynamically with JavaScript.
            // Wait for the page to load, timeout after 10 seconds
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.linkText("New")).isDisplayed();
                }
            });

            // Should see: "cheese! - Google Search"
            System.out.println("Page title is: " + driver.getTitle());

        } catch (WebDriverException e){
            File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
            System.out.println("Screenshot in: " + scrFile.getAbsolutePath());
            fail("Error occured " + e.getMessage());
        } finally {
            // Close the browser
            driver.quit();
        }
    }
}
