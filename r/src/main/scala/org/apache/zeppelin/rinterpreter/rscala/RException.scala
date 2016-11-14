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

package org.apache.zeppelin.rinterpreter.rscala

import org.apache.zeppelin.interpreter.InterpreterResult

class RException(val snippet : String, val error : String, val message : String = "") extends Exception {

  def this(snippet : String) = this(snippet, "")

  def getInterpreterResult : InterpreterResult = new
      InterpreterResult(InterpreterResult.Code.ERROR, message + "\n" + snippet + "\n" + error)

  def getInterpreterResult(st : String) : InterpreterResult = new
      InterpreterResult(InterpreterResult.Code.ERROR, message + "\n" + st + "\n" + error)
}
