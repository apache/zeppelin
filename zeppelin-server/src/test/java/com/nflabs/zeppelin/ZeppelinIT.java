package com.nflabs.zeppelin;

import static org.junit.Assert.fail;

import java.io.File;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ZeppelinIT {
	private WebDriver getWebDriver(){
		WebDriver driver = null;

		if (driver==null){
			try {
				FirefoxBinary ffox = new FirefoxBinary();
				if ("true".equals(System.getenv("TRAVIS"))) {
					ffox.setEnvironmentProperty("DISPLAY", ":99"); // xvfb is supposed to run with DISPLAY 99
				}
				FirefoxProfile profile = new FirefoxProfile();
				driver = new FirefoxDriver(ffox, profile);
			} catch (Exception e){
			}
		}

		if (driver==null){
			try {
				driver = new ChromeDriver();
			} catch (Exception e){
			}
		}

		if (driver==null){
			try {
				driver = new SafariDriver();
			} catch (Exception e){
			}
		}

		String url;
		if (System.getProperty("url")!=null) {
			url = System.getProperty("url");
		} else {
			url = "http://localhost:8080";
		}

		long start = System.currentTimeMillis();
		boolean loaded = false;
		driver.get(url);

		while (System.currentTimeMillis() - start < 60*1000) {
	        // wait for page load
			try {
		        (new WebDriverWait(driver, 5)).until(new ExpectedCondition<Boolean>() {
		            public Boolean apply(WebDriver d) {
		                return d.findElement(By.partialLinkText("Start")).isDisplayed();
		            }
		        });
		        loaded = true;
		        break;
			} catch (TimeoutException e){
				driver.navigate().to(url);
			}
		}

		if (loaded==false) {
			fail();
		}

		return driver;
	}

	@Test
	public void testDisableIT(){
		//
	}
	
	/*
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
                    return d.findElement(By.linkText("Create new Job")).isDisplayed();
                }
            });

            // click new
            driver.findElement(By.linkText("Create new Job")).click();

            // wait for run button appears
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.linkText("Run")).isDisplayed();
                }
            });

            // type some query
            driver.findElement(By.xpath("//div[@id='zqlEditor']//textarea")).sendKeys("create table if not exists test "+Keys.chord(Keys.SHIFT, "9")+"id STRING);\n");
            driver.findElement(By.xpath("//div[@id='zqlEditor']//textarea")).sendKeys("\nshow tables;");

            // press run button
            driver.findElement(By.linkText("Run")).click();

            // wait for button becomes Running ...
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div//a[text()='Running ...']")).isDisplayed();
                }
            });

            // wait for button becomes Run
            (new WebDriverWait(driver, 60)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div//a[text()='Run']")).isDisplayed();
                }
            });

            WebElement msg = driver.findElement(By.id("msgBox"));
            if (msg!=null) {
            	System.out.println("msgBox="+msg.getText());
            }

            // wait for visualization
            (new WebDriverWait(driver, 20)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div[@id='visualizationContainer']//iframe")).isDisplayed();
                }
            });

            WebDriver iframe = driver.switchTo().frame(driver.findElement(By.xpath("//div[@id='visualizationContainer']//iframe")));

            // wait for result displayed
            (new WebDriverWait(iframe, 20)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//table//td[text()='test']")).isDisplayed();
                }
            });
        } catch (WebDriverException e){
            File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
            System.out.println("Screenshot in: " + scrFile.getAbsolutePath());
            throw e;
        } finally {
            // Close the browser
            driver.quit();
        }
    }

*/

    /**
     * Get the url of Zeppelin
     *
     * @param path to add to the url ex: HOST/myPath
     * @return Zeppelin url HOST:PORT{/PATH}
     */
  private String getUrl(String path) {
    String url;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    } else {
      url = "http://localhost:8080";
    }
    if (path != null)
      url += path;
    return url;
  }

/*
    @Test
	public void testZAN() {
		WebDriver driver = getWebDriver();

		try {
			// goto ZAN menu
			driver.findElement(By.xpath("//ul//a[text()='ZAN']")).click();

			// wait for ZAN page loaded
			(new WebDriverWait(driver, 20)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div//a[text()='Update Catalog']")).isDisplayed();
                }
            });
		} catch (WebDriverException e) {
			File scrFile = ((TakesScreenshot) driver)
					.getScreenshotAs(OutputType.FILE);
			System.out.println("Screenshot in: " + scrFile.getAbsolutePath());
			throw e;
		} finally {
			// Close the browser
			driver.quit();
		}
	}
*/


  /**
   * Test is swagger-ui is started
   */
    /*
  @Test
  public void testSwaggerDocumentation() {
    WebDriver driver = getWebDriver();
    try {

      driver.get(getUrl("/docs"));
      // wait for Swagger page loaded
      (new WebDriverWait(driver, 20)).until(new ExpectedCondition<Boolean>() {
        public Boolean apply(WebDriver d) {
          return d.findElement(By.xpath("//div//input[@id='input_apiKey']")).isDisplayed();
        }
      });

    } catch (WebDriverException ex) {
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      System.out.println("Screenshot in: " + scrFile.getAbsolutePath());
      throw ex;
    } finally {
      driver.close();
    }
  }

    @Test
	public void testAnnotationStmt() {
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
                    return d.findElement(By.linkText("Create new Job")).isDisplayed();
                }
            });

            // click new
            driver.findElement(By.linkText("Create new Job")).click();

            // wait for run button appears
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.linkText("Run")).isDisplayed();
                }
            });

            // type some query with default driver
            driver.findElement(By.xpath("//div[@id='zqlEditor']//textarea")).sendKeys("@driver set exec;");
            driver.findElement(By.xpath("//div[@id='zqlEditor']//textarea")).sendKeys("\necho 'hello world';");

            // press run button
            driver.findElement(By.xpath("//div[@id='zqlEditor']//textarea")).sendKeys(Keys.chord(Keys.COMMAND, Keys.ENTER));
            driver.findElement(By.xpath("//div[@id='zqlEditor']//textarea")).sendKeys(Keys.chord(Keys.CONTROL, Keys.ENTER));
            driver.findElement(By.linkText("Run")).click();

            // wait for button becomes Running ...
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div//a[text()='Running ...']")).isDisplayed();
                }
            });

            // wait for button becomes Run
            (new WebDriverWait(driver, 60)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div//a[text()='Run']")).isDisplayed();
                }
            });

            WebElement msg = driver.findElement(By.id("msgBox"));
            if (msg!=null) {
            	System.out.println("msgBox="+msg.getText());
            }

            // wait for visualization
            (new WebDriverWait(driver, 20)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//div[@id='visualizationContainer']//iframe")).isDisplayed();
                }
            });

            WebDriver iframe = driver.switchTo().frame(driver.findElement(By.xpath("//div[@id='visualizationContainer']//iframe")));

            // wait for result displayed
            (new WebDriverWait(iframe, 20)).until(new ExpectedCondition<Boolean>() {
                public Boolean apply(WebDriver d) {
                    return d.findElement(By.xpath("//table//td[text()='hello world']")).isDisplayed();
                }
            });
        } catch (WebDriverException e){
            File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
            System.out.println("Screenshot in: " + scrFile.getAbsolutePath());
            throw e;
        } finally {
            // Close the browser
            driver.quit();
        }
	}
*/	
}
