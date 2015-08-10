package org.apache.zeppelin.rinterpreter.rscala

class ScalaInterpreterAdapter(val interpreter: scala.tools.nsc.interpreter.IMain) extends InterpreterAdapter {

  def interpret(line: String): Unit = interpreter.interpret(line)
  def eval(line: String): Unit = interpreter.interpret(line)
  def bind(name: String, boundType: String, value: Any): Unit = interpreter.bind(name,boundType,value)
  def mostRecentVar: String = interpreter.mostRecentVar
  def valueOfTerm(id: String): Option[Any] = interpreter.valueOfTerm(id)
  def typeOfTerm(id: String): String = interpreter.symbolOfLine(id).info.toString

}

object ScalaInterpreterAdapter {

  def apply(repl: scala.tools.nsc.interpreter.IMain) = new ScalaInterpreterAdapter(repl)

}

