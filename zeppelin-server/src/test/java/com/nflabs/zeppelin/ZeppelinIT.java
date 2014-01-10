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
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.webautomation.ScreenCaptureHtmlUnitDriver;

public class ZeppelinIT {
	private WebDriver getWebDriver(){
		if(System.getProperty("runningFromMaven")==null){
			// zeppelin.daemon.packaged is defined by zeppelin-server/pom.xml
			// assumes it is not running from maven. but eclipse
			return new SafariDriver();
		} else { // assumes running from maven
			ScreenCaptureHtmlUnitDriver driver = new ScreenCaptureHtmlUnitDriver(); //HtmlUnitDriver();
			driver.setJavascriptEnabled(true);
			return driver;
		}
		
	}
	
    @Test
    public void testRunSimpleQueryInNewSession() {
        // Notice that the remainder of the code relies on the interface, 
        // not the implementation.
        WebDriver driver = getWebDriver();
        
        try {
            // go to zeppelin
            driver.get("http://localhost:8080");

            // wait for page load
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.partialLinkText("Start")).isDisplayed();
                }
            });
            // click start
            WebElement start = driver.findElement(By.partialLinkText("Start"));
            start.click();

            // Wait for the page to load, timeout after 10 seconds
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.linkText("New")).isDisplayed();
                }
            });
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
