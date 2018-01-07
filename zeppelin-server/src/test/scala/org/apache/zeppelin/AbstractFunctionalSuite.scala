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

package org.apache.zeppelin

import org.apache.zeppelin.AbstractFunctionalSuite.SERVER_ADDRESS
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

    eventually (timeout(Span(180, Seconds))) {
      go to SERVER_ADDRESS
      assert(find("welcome").isDefined)
    }
  }

  override def nestedSuites =
    List[Suite](new WelcomePageSuite).toIndexedSeq

  override def afterAll() = {
    "../bin/zeppelin-daemon.sh stop" !

    webDriver.close()
  }

  def getDriver(): WebDriver = {
    val possibleDrivers = List[() => WebDriver](safari, chrome, firefox)
    val createdDriver = possibleDrivers.map(driverFactory => Try(driverFactory.apply())).find(_.isSuccess)
    createdDriver match {
      case Some(driver) => driver.get
      case None => throw new RuntimeException("Could not initialize any driver")
    }
  }

  def safari(): WebDriver = {
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
