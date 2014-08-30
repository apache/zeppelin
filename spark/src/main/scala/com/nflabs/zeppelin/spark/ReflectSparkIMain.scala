package com.nflabs.zeppelin.spark

import scala.tools.nsc._
import scala.tools.nsc.interpreter._
import reporters._
import org.apache.spark.repl.SparkIMain
import scala.tools.reflect._
class ReflectSparkIMain(initialSettings: Settings, override val out: JPrintWriter) extends SparkIMain(initialSettings, out) {
	
  override def newCompiler(settings: Settings, reporter: Reporter): ReplGlobal = {
    settings.outputDirs setSingleOutput virtualDirectory
    settings.exposeEmptyPackage.value = true
    new ReflectGlobal(settings, reporter, classLoader) with ReplGlobal {
      override def toString: String = "<global>"
    }
  }
}