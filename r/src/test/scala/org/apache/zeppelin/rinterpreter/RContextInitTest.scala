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

package org.apache.zeppelin.rinterpreter

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}

import org.apache.zeppelin.rinterpreter.rscala.RClient
import org.apache.zeppelin.rinterpreter.rscala.RClient._
import org.scalatest.Matchers._
import org.scalatest._

class RContextInitTest extends FlatSpec {
  import scala.sys.process._
  var cmd: PrintWriter = null
  val command = RClient.defaultRCmd +: RClient.defaultArguments
  var processCmd : ProcessBuilder = null

    "Process command" should "create a process builder" in {
      processCmd = Process(command)
      processCmd shouldBe a[ProcessBuilder]
    }
  it should "be persistent for testing purposes" in {
    processCmd shouldBe a [ProcessBuilder]
  }

  var processIO : ProcessIO = null

  "Creating Process IO" should "not throw an exception" in {
    processIO = new ProcessIO(
      o => {
        cmd = new PrintWriter(o)
      },
      reader("STDOUT DEBUG: "),
      reader("STDERR DEBUG: "),
      true
    )
    processIO shouldBe a [ProcessIO]
  }
  var portsFile : File = null
    "A temp file " should "be created" in {
      portsFile = File.createTempFile("rscala-", "")
      assertResult(true) {portsFile.exists()}
    }
  var processInstance : Process = null

  "Process instance" should "launch" in {
    processInstance = processCmd.run(processIO)
    assert(true)
  }
  var libpath : String = null
  "RZeppelin R Package" should "be found" in {
    libpath  = if (Files.exists(Paths.get("R/lib"))) "R/lib"
    else if (Files.exists(Paths.get("../R/lib"))) "../R/lib"
    else throw new RuntimeException("Could not find rzeppelin - it must be in either R/lib or ../R/lib")
    assert(Files.exists(Paths.get(libpath + "/rzeppelin")))
  }
  var snippet : String = null

  "Creating the snippit" should "be impossible to fail" in {
    snippet =     s"""
library(lib.loc="$libpath", rzeppelin)
rzeppelin:::rServe(rzeppelin:::newSockets('${portsFile.getAbsolutePath.replaceAll(File.separator, "/")}',debug=FALSE,timeout=60))
q(save='no')"""
    assert(true)
  }
  "Cmd" should "stop being null" in {
    while (cmd == null) Thread.sleep(100)
    assert(cmd != null)
  }
  it should "accept the snippet" in {
    cmd.println(snippet)
    cmd.flush()
    assert(true)
  }

  var sockets : ScalaSockets = null

  "Scala Sockets" should "be created and signal OK" in {
    sockets = new ScalaSockets(portsFile.getAbsolutePath)
    sockets.out.writeInt(RClient.Protocol.OK)
    sockets.out.flush()
    assert(true)
  }
  "The R and Scala versions" should "match" in {
    assert(RClient.readString(sockets.in) == org.apache.zeppelin.rinterpreter.rscala.Version)
  }
  var rcon : RContext = null
  "Creating an RContext" should "not fail" in {
    rcon = new RContext(sockets, false)
  }
  "An open RContext" should "destroy safely" in {
    rcon.close()
    assertResult(false) {
      rcon.isOpen
    }
  }
}