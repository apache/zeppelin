package org.apache.zeppelin.rinterpreter



import java.util._

import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.{InterpreterContext, _}
import org.apache.zeppelin.rinterpreter.rscala.RException
import org.apache.zeppelin.scheduler.Scheduler
import org.apache.zeppelin.spark.{SparkInterpreter, ZeppelinContext}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions.asScalaBuffer
import scala.util.matching.Regex

abstract class RInterpreter(properties : Properties) extends Interpreter (properties) {
  def this() = {
    this(new Properties())
  }

  private val logger: Logger = LoggerFactory.getLogger(getClass)
  logger.info("Initialising an RInterpreter of class " + this.getClass.getName)

  def getrContext : RContext = rContext
  lazy val rContext : RContext = RInterpreter.synchronized {RContext(properties)}

  override def setInterpreterGroup(interpreterGroup: InterpreterGroup) : Unit = {
    logger.info("Setting interpreter group" + interpreterGroup.toString())
    super.setInterpreterGroup(interpreterGroup)
    rContext.setInterpreterGroup(interpreterGroup)
    logger.info("Setting interpreter group" + interpreterGroup.toString() + " in rContext")
  }

//  override def getScheduler() : Scheduler =  rContext.scheduler

  def open: Unit = {
    logger.warn("RInterpreter opening")
    rContext.synchronized {
                            rContext.open()
                          }
    logger.warn("RInterpreter open")
  }

  def close: Unit = rContext.close

  def getProgress(context : InterpreterContext) : Int  = 0

  def cancel(context: InterpreterContext) : Unit = {}

  def getFormType: FormType = {
    return FormType.NONE
  }

  override def getScheduler : Scheduler = rContext.getScheduler

import scala.collection.JavaConversions.seqAsJavaList
 def completion(buf: String, cursor: Int): List[String] = seqAsJavaList(rContext.evalS1(s""".zcompletion("${buf}", ${cursor})"""))

def testPackage(pack : String, fail : Boolean = false, license : Boolean = false) : Boolean =
  rContext.synchronized {
                          rContext.evalB0(s"require(${pack})") match {
                            case true => true
                            case false => {
                              val message: String = s"""The ${pack} package could not be loaded.""" +
                                                    {if (license) "We cannot install it for you because it is published under the GPL3 license."

                                                    else ""}
                              if (fail) throw new RException(message)
                              return false
                            }
                          }
                        }
}

object RInterpreter {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  logger.info("logging inside the RInterpreter singleton")

  lazy val props: Map[String, InterpreterProperty] = new InterpreterPropertyBuilder()
         .add("spark.home", SparkInterpreter.getSystemDefault("SPARK_HOME", "spark.home", ""),
              "Spark home path. Should be provided for SparkR")
          .add("r.home", SparkInterpreter.getSystemDefault("R_HOME", "r.home", ""), "R home path.")
          .add("rscala.home", SparkInterpreter.getSystemDefault("RSCALA_HOME", "rscala.home", ""), "Path to library directory containing rScala R Package")
          .add("rhadoop.cmd", SparkInterpreter.getSystemDefault("HADOOOP_CMD", "rhadoop.cmd", ""), "Usually /usr/bin/hadoop")
          .add("rhadooop.streamingjar", SparkInterpreter.getSystemDefault("HADOOP_STREAMING", "rhadoop.streamingjar", ""), "Usually /usr/lib/hadoop/contrib/streaming/hadoop-streaming-<version>.jar")
          .add("rscala.debug", "false", "Whether to turn on rScala debugging") // FIXME:  Implemented but not tested
          .add("rscala.timeout", "60", "Timeout for rScala") // TODO:  Not yet implemented
         .build

  def getProps() = {
//    register()
    props
  }

  val scriptRegex: Regex = new Regex( """(?s)(?!body)(<script .*?</script>)""".stripMargin)

  def processHTML(input: String): String = {
    val scripts: String = scriptRegex.findAllMatchIn(input).mkString("\n") + "\n"
    scripts + (input.substring(input.indexOf("<body>") + 6, input.indexOf("</body>") - 1) replaceAll
      ("<code>", "") replaceAll
      ("</code>", "") replaceAll
 //     ("\n\n", "\n") replaceAll
      //     ("\n", "<br>") replaceAll
      ("<pre>", "<p class='text'>") replaceAll
      ("</pre>", "</p>"))
  }

  def processHTML(input: List[String]): String = processHTML(input.mkString("\n"))

}
