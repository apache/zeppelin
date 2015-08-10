package org.apache.zeppelin.rinterpreter.rscala

trait InterpreterAdapter {

  val interpreter: Any
  def interpret(line: String): Unit
  def eval(line: String): Unit
  def bind(name: String, boundType: String, value: Any): Unit
  def mostRecentVar: String
  def valueOfTerm(id: String): Option[Any]
  def typeOfTerm(id: String): String

}

