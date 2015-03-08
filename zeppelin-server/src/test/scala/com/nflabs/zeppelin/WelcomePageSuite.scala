package com.nflabs.zeppelin

import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.Eventually._
import org.scalatest.time._
import org.scalatest.selenium.WebBrowser
import org.scalatest.{DoNotDiscover, FunSuite}
import AbstractFunctionalSuite.SERVER_ADDRESS

@DoNotDiscover
class WelcomePageSuite(implicit driver: WebDriver) extends FunSuite with WebBrowser {

  test("Welcome sign is correct") {
    eventually (timeout(Span(20, Seconds))) {
      go to SERVER_ADDRESS
      assert(find("welcome").isDefined)
    }
  }

}
