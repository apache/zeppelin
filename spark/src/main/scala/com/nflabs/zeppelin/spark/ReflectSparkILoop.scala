package com.nflabs.zeppelin.spark

import scala.tools.nsc._
import scala.tools.nsc.interpreter._
import org.apache.spark.repl.SparkILoop
import org.apache.spark.repl.SparkIMain
import org.apache.spark.util.Utils
import java.io.BufferedReader
import scala.tools.nsc.util.{ ClassPath, Exceptional, stringFromWriter, stringFromStream }


class ReflectSparkILoop(in0: Option[BufferedReader], override protected val out: JPrintWriter, override val master: Option[String])
	  extends SparkILoop(in0, out, master) {
  def this(in0: BufferedReader, out: JPrintWriter, master: String) = this(Some(in0), out, Some(master))
  def this(in0: BufferedReader, out: JPrintWriter) = this(Some(in0), out, None)
  def this() = this(None, new JPrintWriter(Console.out, true), None)  
  

  class ReflectSparkILoopInterpreter extends ReflectSparkIMain(settings, out) {
    outer =>

    override lazy val formatting = new Formatting {
      def prompt = ReflectSparkILoop.this.prompt
    }
    override protected def parentClassLoader = SparkHelper.explicitParentLoader(settings).getOrElse(classOf[SparkILoop].getClassLoader)
  }

  /** Create a new interpreter. */
  override def createInterpreter() {
    require(settings != null)

    if (addedClasspath != "") settings.classpath.append(addedClasspath)
    // work around for Scala bug
    val totalClassPath = SparkILoop.getAddedJars.foldLeft(
      settings.classpath.value)((l, r) => ClassPath.join(l, r))
    this.settings.classpath.value = totalClassPath

    intp = new ReflectSparkILoopInterpreter
  }
  
  /** Create a new interpreter. */
  def createReflectInterpreter(settings : Settings) : SparkIMain = {
    require(settings != null)

    if (addedClasspath != "") settings.classpath.append(addedClasspath)
    // work around for Scala bug
    val totalClassPath = SparkILoop.getAddedJars.foldLeft(
      settings.classpath.value)((l, r) => ClassPath.join(l, r))
    this.settings.classpath.value = totalClassPath

    intp = new ReflectSparkILoopInterpreter
    intp
  }
}