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
		WebDriver driver;
		if (System.getProperty("runningFromMaven")==null) {
			// zeppelin.daemon.packaged is defined by zeppelin-server/pom.xml
			// assumes it is not running from maven. but eclipse
			driver = new SafariDriver();
		} else { // assumes running from maven
			ScreenCaptureHtmlUnitDriver htmlUnitDriver = new ScreenCaptureHtmlUnitDriver(); //HtmlUnitDriver();
			htmlUnitDriver.setJavascriptEnabled(true);
			driver = htmlUnitDriver;
		}	
		
		String url;
		if (System.getProperty("url")!=null) {
			url = System.getProperty("url");
		} else {
			url = "http://localhost:8080";
		}
		driver.get(url);
		
        // wait for page load
        (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
            public Boolean apply(WebDriver d) {
                return d.findElement(By.partialLinkText("Start")).isDisplayed();
            }
        });

		return driver;
	}
	
    @Test
    public void testRunSimpleQueryInNewSession() {
        // Notice that the remainder of the code relies on the interface, 
        // not the implementation.
        WebDriver driver = getWebDriver();
        
        try {
            // click start
            WebElement start = driver.findElement(By.partialLinkText("Start"));
            start.click();

            // Wait for the page to load, timeout after 10 seconds
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.linkText("New")).isDisplayed();
                }
            });
            
            // click new
            driver.findElement(By.linkText("New")).click();
            
            // wait for run button appears
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.linkText("Run")).isDisplayed();
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
