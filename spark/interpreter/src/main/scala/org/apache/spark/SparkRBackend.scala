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
package org.apache.spark

import org.apache.spark.api.r.RBackend

object SparkRBackend {
  val backend : RBackend = new RBackend()
  private var started = false;
  private var portNumber = 0;
  private var secret: String = "";

  val backendThread : Thread = new Thread("SparkRBackend") {
    override def run() {
      backend.run()
    }
  }

  def init() : Unit = {
    val rBackendClass = classOf[RBackend]
    rBackendClass.getMethod("init").invoke(backend) match {
        // A secret is used in newer versions of Spark to encrypt traffic
      case (port: Int, rAuthHelper: AnyRef) =>
        portNumber = port
        secret = rAuthHelper.getClass.getMethod("secret").invoke(rAuthHelper).asInstanceOf[String]
      case port: java.lang.Integer =>
        portNumber = port
    }
  }

  def start() : Unit = {
    backendThread.start()
    started = true
  }

  def close() : Unit = {
    backend.close()
    backendThread.join()
  }

  def isStarted() : Boolean = started

  def port(): Int = portNumber

  def socketSecret(): String = secret;
}
