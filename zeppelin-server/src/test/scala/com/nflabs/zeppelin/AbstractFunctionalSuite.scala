package com.nflabs.zeppelin

import com.nflabs.zeppelin.AbstractFunctionalSuite.SERVER_ADDRESS
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.{FirefoxBinary, FirefoxDriver, FirefoxProfile}
import org.openqa.selenium.safari.SafariDriver
import org.scalatest.concurrent.Eventually._
import org.scalatest.time._
import org.scalatest.selenium.WebBrowser
import org.scalatest.{BeforeAndAfterAll, FunSuite, Suite}

import scala.sys.process._
import scala.util.Try

object AbstractFunctionalSuite {
  val SERVER_ADDRESS = "http://localhost:8080"
}

class AbstractFunctionalSuite extends FunSuite with WebBrowser with BeforeAndAfterAll {

  implicit val webDriver = getDriver()

  override def beforeAll() = {
    "../bin/zeppelin-daemon.sh start" !

    eventually (timeout(Span(20, Seconds))) {
      go to SERVER_ADDRESS
      assert(find("welcome").isDefined)
    }
  }

  override def nestedSuites =
    List[Suite](new WelcomePageSuite).toIndexedSeq

  override def afterAll() = {
    "../bin/zeppelin-daemon.sh stop" !
  }

  def getDriver(): WebDriver = {
    val possibleDrivers = List[() => WebDriver](safary, chrome, firefox)
    val createdDriver = possibleDrivers.map(driverFactory => Try(driverFactory.apply())).find(_.isSuccess)
    createdDriver match {
      case Some(driver) => driver.get
      case None => throw new RuntimeException("Could not initialize any driver")
    }
  }

  def safary(): WebDriver = {
    new SafariDriver()
  }

  def chrome(): WebDriver = {
    new ChromeDriver()
  }

  def firefox(): WebDriver = {
    val ffox: FirefoxBinary = new FirefoxBinary
    if ("true" == System.getenv("TRAVIS")) {
      ffox.setEnvironmentProperty("DISPLAY", ":99")
    }
    val profile: FirefoxProfile = new FirefoxProfile
    new FirefoxDriver(ffox, profile)
  }
}
