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

package org.apache.zeppelin.scio.util

import java.io.{ByteArrayOutputStream, PrintStream}

import com.google.common.base.Charsets
import com.spotify.scio.ScioContext
import com.spotify.scio.values.SCollection
import org.apache.zeppelin.scio.DisplayHelpers

import scala.reflect.ClassTag

object TestUtils {
  val tab = DisplayHelpers.tab
  val newline = DisplayHelpers.newline
  val table = DisplayHelpers.table
  val rowLimitReached = DisplayHelpers.rowLimitReachedMsg.replaceAll(newline,"")

  private[scio] def sideEffectWithData[T: ClassTag](data: Iterable[T])
                                                   (fn: SCollection[T] => Unit): Unit = {
    val sc = ScioContext()
    fn(sc.parallelize(data))
    if (!sc.isClosed) sc.close()
  }

  private[scio] def captureOut[T](body: => T): Seq[String] = {
    val bytes = new ByteArrayOutputStream()
    val stream = new PrintStream(bytes)
    Console.withOut(stream) { body }
    bytes.toString(Charsets.UTF_8.toString).split(DisplayHelpers.newline)
  }


}
