package com.nflabs.zeppelin.spark

import com.nflabs.zeppelin.repl.Repl
import com.nflabs.zeppelin.repl.ReplResult
import com.nflabs.zeppelin.repl.ReplResult.Code
import com.nflabs.zeppelin.repl.Repl.FormType
import java.util.Properties
import scala.tools.nsc.{Interpreter, Settings}
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import org.apache.spark.repl.SparkILoop
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.SparkContext

class SparkRepl(properties: Properties) extends Repl(properties) {
  
  val out = new ByteArrayOutputStream(); 
  var interpreter : ReflectSparkILoop = _
  var intp : SparkIMain = _
  
  protected def getSparkContext() : SparkContext = {
    null
  }
  
  override def initialize() = {
    val cl = Thread.currentThread().getContextClassLoader();
    
    val settings = new Settings();
    settings.usejavacp.value = true

    val printStream = new PrintStream(out)
    interpreter = new ReflectSparkILoop(null, new PrintWriter(out))
    interpreter.settings = settings;
    intp = interpreter.createReflectInterpreter(settings);
    interpreter.intp = intp
    intp.initializeSynchronous
    
    
  }
  override def destroy() = {
	intp.close()	
  }
  override def getValue(name : String) : Object = {

    return null;
  }
  override def interpret(st : String) : ReplResult = {
    return null;
  }
	
  override def cancel() = {
	  
  }
  override def bindValue(name : String, o : Object) = {
	  
  }
  override def getFormType() : FormType = {
    return FormType.NATIVE;
  }
  
  def getResultCode(r : scala.tools.nsc.interpreter.Results.Result) : Code = {

    if (r.isInstanceOf[instanceof scala.tools.nsc.interpreter.Results.Success$) {
      return Code.SUCCESS;
    } else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
      return Code.INCOMPLETE;
    } else {
      return Code.ERROR;
	}
  }
}