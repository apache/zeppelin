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

package org.apache.zeppelin.scalding

/**
  * Stores REPL state
  */

import cascading.flow.FlowDef
import com.twitter.scalding.BaseReplState
import scala.concurrent.{ ExecutionContext => ConcurrentExecutionContext }
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ZeppelinReplState extends BaseReplState {
  override def shell = ZeppelinScaldingShell
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
