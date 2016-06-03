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

import java.io.BufferedReader
import com.twitter.scalding.ScaldingILoop

import scala.tools.nsc.interpreter._

/**
  * TBD
  */
class ZeppelinScaldingILoop(in: Option[BufferedReader], out: JPrintWriter)
  extends ScaldingILoop(in, out) {

  override protected def imports = List(
    "com.twitter.scalding.{ ScaldingILoop => ScaldingScaldingILoop, ScaldingShell => ScaldingScaldingShell, _ }",
    // ReplImplicits minus fields API parts (esp FieldConversions)
    """com.twitter.scalding.ReplImplicits.{
      iterableToSource,
      keyedListLikeToShellTypedPipe,
      typedPipeToShellTypedPipe,
      valuePipeToShellValuePipe
    }""",
    "com.twitter.scalding.ReplImplicits",
    "org.apache.zeppelin.scalding.ZeppelinReplImplicitContext._",
    "org.apache.zeppelin.scalding.ZeppelinReplState",
    "org.apache.zeppelin.scalding.ZeppelinReplState._"
  )

}
