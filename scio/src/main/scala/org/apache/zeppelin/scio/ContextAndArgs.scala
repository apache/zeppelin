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

package org.apache.zeppelin.scio

import com.google.cloud.dataflow.sdk.options.PipelineOptions
import com.spotify.scio.repl.ReplScioContext
import com.spotify.scio.{Args, ScioContext}

/**
 * Convenience object for creating [[com.spotify.scio.ScioContext]] and [[com.spotify.scio.Args]].
 */
object ContextAndArgs {
  def apply(argz: Array[String]): (ScioContext, Args) = {
    val (dfOpts, args) = ScioContext.parseArguments[PipelineOptions](argz)

    val nextReplJar = this
      .getClass
      .getClassLoader
      .asInstanceOf[{def getNextReplCodeJarPath: String}].getNextReplCodeJarPath

    val sc = new ReplScioContext(dfOpts, List(nextReplJar))
    sc.setName("sciozeppelin")

    (sc, args)
  }
}
