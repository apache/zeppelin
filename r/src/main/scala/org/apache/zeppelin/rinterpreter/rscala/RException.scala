package org.apache.zeppelin.rinterpreter.rscala

import _root_.org.apache.zeppelin.interpreter.InterpreterResult

/**
 * Created by aelberg on 8/4/15.
 */
class RException(val snippet : String, val error : String, val message : String = "") extends Exception {

  def this(snippet : String) = this(snippet, "")

  def getInterpreterResult : InterpreterResult = new
      InterpreterResult(InterpreterResult.Code.ERROR, message + "\n" + snippet + "\n" + error)

  def getInterpreterResult(st : String) : InterpreterResult = new
      InterpreterResult(InterpreterResult.Code.ERROR, message + "\n" + st + "\n" + error)
}
