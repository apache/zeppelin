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

import org.apache.zeppelin.scio.util.TestUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

/**
 * Scala 2.11 DisplayHelpersTest tests.
 *
 * Most tests have test scope implicit imports due to scala 2.10 bug
 * https://issues.scala-lang.org/browse/SI-3346
 *
 * Note: we can't depend on the order of data coming from SCollection.
 */
@RunWith(classOf[JUnitRunner]
class DisplayHelpersTestScala211 extends FlatSpec with Matchers {
  import TestUtils._

  // -----------------------------------------------------------------------------------------------
  // Product SCollection Tests
  // -----------------------------------------------------------------------------------------------

  it should "support SCollection of Case Class of 23" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    val tupleHeader = s"$table " + (1 to 22).map(i => s"a$i$tab").mkString + "a23"
    val o = captureOut {
      sideEffectWithData(
        Seq.fill(3)(CC23(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs (Seq(tupleHeader) ++
      Seq.fill(3)((1 to 22).map(i => s"$i$tab").mkString + "23"))
    o.head should be(tupleHeader)
  }

}
