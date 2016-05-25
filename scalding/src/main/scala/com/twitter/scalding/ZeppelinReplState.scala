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

package com.twitter.scalding

/**
  * Stores REPL state
  */

import cascading.flow.FlowDef
import scala.concurrent.{ ExecutionContext => ConcurrentExecutionContext }
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ZeppelinReplState extends BaseReplState {
  override def shell = ZeppelinScaldingShell

  /** Create config for execution. Tacks on a new jar for each execution.
    * We have to use executionConfig1 and override run, asyncExecute, execute since we need
    * to use ZeppelinScaldingShell.createReplCodeJar1. See comment for that method.
    */
  def executionConfig1: Config = {
    // Create a jar to hold compiled code for this REPL session in addition to
    // "tempjars" which can be passed in from the command line, allowing code
    // in the repl to be distributed for the Hadoop job to run.
    val replCodeJar: Option[java.io.File] = shell.createReplCodeJar1()
    val tmpJarsConfig: Map[String, String] =
      replCodeJar match {
        case Some(jar) =>
          Map("tmpjars" -> {
            // Use tmpjars already in the configuration.
            config.get("tmpjars").map(_ + ",").getOrElse("")
              // And a jar of code compiled by the REPL.
              .concat("file://" + jar.getAbsolutePath)
          })
        case None =>
          // No need to add the tmpjars to the configuration
          Map()
      }
    config ++ tmpJarsConfig
  }

  /**
    * Runs this pipe as a Scalding job.
    *
    * Automatically cleans up the flowDef to include only sources upstream from tails.
    */
  override def run(implicit fd: FlowDef, md: Mode): Option[JobStats] =
    ExecutionContext.newContext(executionConfig1)(fd, md).waitFor match {
      case Success(stats) => Some(stats)
      case Failure(e) =>
        println("Flow execution failed!")
        e.printStackTrace()
        None
    }

  /*
   * Starts the Execution, but does not wait for the result
   */
  override def asyncExecute[T](execution: Execution[T])(implicit ec: ConcurrentExecutionContext): Future[T] =
    execution.run(executionConfig1, mode)

  /*
   * This runs the Execution[T] and waits for the result
   */
  override def execute[T](execution: Execution[T]): T =
    execution.waitFor(executionConfig1, mode).get


}

/**
  * Implicit FlowDef and Mode, import in the REPL to have the global context implicitly
  * used everywhere.
  */
object ZeppelinReplImplicitContext {
  /** Implicit execution context for using the Execution monad */
  implicit val executionContext = ConcurrentExecutionContext.global
  /** Implicit repl state used for ShellPipes */
  implicit def stateImpl = ZeppelinReplState
  /** Implicit flowDef for this Scalding shell session. */
  implicit def flowDefImpl = ZeppelinReplState.flowDef
  /** Defaults to running in local mode if no mode is specified. */
  implicit def modeImpl = ZeppelinReplState.mode
  implicit def configImpl = ZeppelinReplState.config
}
