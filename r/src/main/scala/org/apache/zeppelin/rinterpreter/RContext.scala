package org.apache.zeppelin.rinterpreter

import java.io._
import java.util
import java.util.Properties

import org.slf4j._
import org.apache.zeppelin.scheduler._
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.zeppelin.interpreter._
import org.apache.zeppelin.rinterpreter.rscala._
import org.apache.zeppelin.spark.{SparkInterpreter, ZeppelinContext}
import RClient._

import Protocol._


import scala.collection.JavaConversions._

import scala.util.Try
import scala.concurrent._

/**
 * Created by aelberg on 7/26/15.
 */
class RContext (private val in: DataInputStream,
                private val out: DataOutputStream,
                debug: Boolean = true) extends RClient (in , out,  debug) {

	private val logger: Logger = LoggerFactory.getLogger(getClass)

  lazy val getScheduler: Scheduler = SchedulerFactory.singleton().createOrGetFIFOScheduler(this.hashCode().toString)

	logger.info("RContext starting up")
  private lazy val property                                       = propertyOutput.value.get.get
  private lazy val sc              : Option[SparkContext]         = getSparkInterpreter match {
	  case Some(x: SparkInterpreter) => {
		  logger.info("have a spark interpreter, getting a context")
		  Some(x.getSparkContext)
	  }
    case _ => None
  }
  private lazy val sqlc            : Option[SQLContext]           = getSparkInterpreter match {
    case Some(x: SparkInterpreter) => Some(x.getSQLContext)
    case None => None
  }
  private lazy val z               : util.HashMap[String, Object] = getSparkInterpreter match {
    case Some(x: SparkInterpreter) => x.getZeppelinContext
    case _ => new util.HashMap[String, Object]()
  }
  private lazy val sparkEnvir                                     = (property - "spark.app.id" -
    "spark.app.name" - "spark.driver.port" - "spark.tachyonStore.folderName" - "spark.repl.class.uri" -
    "spark.fileserver.uri" - "spark.master" - "spark.jars")
    .map({ case (key: String, value: String) => key + " = \"" + value + "\" " }).mkString(",")
  // A reference to the R object embodying the Spark Context
  // In an ideal world this would use the existing spark context.

	private val sparkRContextInput: Promise[RObjectRef] = Promise[RObjectRef]()
	private val sparkRContext     : Future[RObjectRef]  = sparkRContextInput.future

	private def getSparkRContext(): Option[RObjectRef] = this.synchronized {
		                                                                       logger.info("Getting SparkR Context")
		                                                                       if (sparkRContext
			                                                                       .isCompleted) {
			                                                                       return sparkRContext.value
				                                                                       .get.toOption
		                                                                       }
		                                                                       logger.info("Making new SparkR Context")
		                                                                       extractSparkConf()
		                                                                       logger.info("Finished getting a spark conf")
		                                                                       val sparkHome = getProperty(
			                                                                       "spark.home",
			                                                                       "/usr/local/spark")
		                                                                       if (!evalB0( s"""require(lib.loc=\"${sparkHome}/R/lib\", SparkR)""")) {
			                                                                       throw new InterpreterException(
				                                                                       "SparkR package not installed")
		                                                                       }
		                                                                       logger.info("Loaded SparkR")
		                                                                       val sparkMaster = getProperty("spark.master")
		                                                                       val sparkJars = getProperty("spark.jars")
		                                                                       val commandParams = s"""master="${sparkMaster}",
		                                                                                               |sparkHome = "${sparkHome}",
		                                                                                               |sparkEnvir = "${sparkEnvir}
		                                                                                               |""".stripMargin + {
			                                                                       if (sparkJars.length > 0) {
				                                                                       "," + sparkJars
			                                                                       }
			                                                                       else {
				                                                                       ""
			                                                                       }
		                                                                       }
		                                                                       logger.info(commandParams)
		                                                                       val command = s"""sc <- SparkR::sparkR.init(${commandParams})"""
			                                                                       .stripMargin
		                                                                       logger.info(command)
		                                                                       eval(command)
		                                                                       val result = getR("sc")
		                                                                       if (!testRObjectClass(result, "jobj")) {
			                                                                       logger
				                                                                       .warn("Spark Context is not a jobj")
		                                                                       }
		                                                                       sparkRContextInput.success(result)
		                                                                       getSparkRContext
	                                                                       }

	private val sparkRSQLContextInput: Promise[RObjectRef] = Promise[RObjectRef]()
	private val sparkRSQLContext     : Future[RObjectRef]  = sparkRSQLContextInput.future

	def getSparkRSQLContext(): Option[RObjectRef] = this.synchronized {
		                                                                  if (!sparkRContext.isCompleted) {
			                                                                  logger
				                                                                  .info(
					                                                                  "Tried to get a SQL Context without a Spark Context")
			                                                                  return None
		                                                                  }
		                                                                  if (sparkRSQLContext
			                                                                  .isCompleted) {
			                                                                  return sparkRSQLContext.value.get
				                                                                  .toOption
		                                                                  }
		                                                                  evalR( s"""sqlContext <- sparkRSQL.init(sc)""")
		                                                                  val result = getR("sqlContext")
		                                                                  if (!testRObjectClass(result, "jobj")) {
			                                                                  logger.warn("Spark SQL Context is not a jobj")
		                                                                  }
		                                                                  sparkRSQLContextInput.success(result)
		                                                                  getSparkRSQLContext
	                                                                  }

	private val sparkRHiveContextInput: Promise[RObjectRef] = Promise[RObjectRef]()
	private val sparkRHiveContext     : Future[RObjectRef]  = sparkRHiveContextInput.future

	def getSparkRHiveContext(): Option[RObjectRef] = this.synchronized {
		                                                                   if (!sparkRContext.isCompleted) {
			                                                                   logger
				                                                                   .info(
					                                                                   "Tried to get a Hive Context without a Spark Context")
			                                                                   return None
		                                                                   }
		                                                                   if (sparkRHiveContext
			                                                                   .isCompleted) {
			                                                                   return sparkRHiveContext.value
				                                                                   .get.toOption
		                                                                   }
		                                                                   evalR( s"""sqlContext <- sparkRHive.init(sc)""")
		                                                                   val result = getR("sparkRHive")
		                                                                   if (!testRObjectClass(result, "jobj")) {
			                                                                   logger.warn("Spark Hive Context is not a jobj")
		                                                                   }
		                                                                   sparkRHiveContextInput.success(result)
		                                                                   getSparkRHiveContext
	                                                                   }



  private      val propertyInput   : Promise[Properties]          = Promise[Properties]()
  private      val propertyOutput  : Future[Properties]           = propertyInput.future
  private      val sparkInterpreterP                              = Promise[SparkInterpreter]()
  private      val sparkInterpreter                               = sparkInterpreterP.future
  private      var interpreterGroup: InterpreterGroup             = null
  private      var isOpen          : Boolean                      = false

  def getProperty(key: String) = property.getProperty(key)

  import java.util.HashMap

  def getProperty(key: String, default: String) = property.getProperty(key, default)

  def setProperty(properties: Properties): RContext = synchronized {
	                                                                   propertyOutput.isCompleted match {
		                                                                   case false => propertyInput.success(properties)
		                                                                   case true => propertyOutput.value.get.get
			                                                                   .putAll(properties)
	                                                                   }
	                                                                   this
                                                                   }

  def setProperty(key: String, value: String): Unit = property.put(key, value)

  def getInterpreterGroup: InterpreterGroup = interpreterGroup

  def setInterpreterGroup(newgroup: InterpreterGroup): Unit = {
    logger.debug("Setting rContext interpreter group")
    interpreterGroup = newgroup
  }

  def getZeppelinContext: HashMap[String, Object] = z

  def updateZeppelinContext() : Unit = this.synchronized {
     logger.info("Updating Zeppelin Context")
     val len : Int = evalI0("length(.zbuffer)")
     if (len == 0) return
     for (i <- List.range(1, len)) {
       val identifier : String = evalS0(s"names(.zbuffer[${i}])")
	     val obj: RObject = ??? // TODO:  Implement getting objects for zeppelin context
       z.put(identifier, obj)
     }
     eval(".zbuffer <- new.env()")
   }

  def open(): Unit = this.synchronized {
	                                       isOpen match {
		                                       case true => {
			                                       logger.info("Reusing rContext.")
		                                       }
		                                       case false => {
			                                       try { {
				                                       logger.info("Opening rContext")
				                                       eval(".zeppenv <- new.env()")
				                                       eval(".zbuffer <- new.env()")
				                                       eval(
					                                       """
					                                         |z.put <- function(identifier, object) {
					                                         |   assign(identifier, object, envir = .zbuffer)
					                                         |}
					                                       """.
						                                       stripMargin)

				                                       eval(
					                                       """.zcompletion <- function(buf, cursor) {
					                                         |utils:::.assignLinebuffer(buf)
					                                         |utils:::.assignEnd(cursor)
					                                         |utils:::.guessTokenFromLine()
					                                         |utils:::.completeToken()
					                                         |utils:::.retrieveCompletions()
					                                         |}""".stripMargin)
			                                       }
			                                       } catch {
				                                       case e: Exception => {
					                                       logger.error(
						                                       "Error opening rContext", e)
					                                       throw new
							                                       InterpreterException(
								                                       "rContext could not open " + e.getMessage)
				                                       }
			                                       }
			                                       isOpen = true
			                                       // Now that we're open, build the spark context
			                                       buildSparkContexts()
			                                       buildRHadoop()
		                                       }
	                                       }
                                       }

	// FIXME:  Getting a match error somewhere around here
	private def buildSparkContexts(): Boolean = try { {
		getSparkRContext() match {
			case Some(x: RObjectRef) ⇒ {
				logger.info("SparkR Context Active")
				getSparkRSQLContext match {
					case Some(x: RObjectRef) ⇒ logger.info("Spark SQL Context Active")
					case None ⇒ logger.info("Spark SQL Context Inactive")
				}
				getSparkRHiveContext() match {
					case Some(x: RObjectRef) ⇒ logger.info("Spark Hive Context Active")
					case None ⇒ logger.info("Spark Hive Context Inactive")
				}
				true
			}
			case None ⇒ false
		}
	}
	} catch {
		case e: Exception => logger.error("Could not create sparkR context " + e.getMessage + e.getStackTrace)
			return false
	} finally {
		return true
	}

	// TODO:  Implement setting the environment based on properties; add ravro, etc.
	private def buildRHadoop(): Boolean = try { {
		evalB0(s"require(rmr2)") match {
			case true => {
				logger.info("RMR2 Loaded")
				true
			}
			case false => {
				logger.info("The rmr2 package could not be loaded.")
				false
			}
		}
	}
	}


  def close: Unit = if (isOpen) {
    exit()
    isOpen = false
  }

  // Note:  This isn't getting default properties, and it should
  def getProperty: Properties = property

  def testRObjectClass(obj: RObjectRef, target: String): Boolean = !getRObjectClass(obj).contains(target)

  def getRObjectClass(obj: RObjectRef) = this.synchronized {evalS1("class(${obj})").toList}

  def describeRObject(obj: RObjectRef): String = this
    .synchronized {evalS0(s"str(${obj})")} // This doesn't quite work yet

  private def getSparkInterpreter(): Option[SparkInterpreter] = {
	  logger.info("Going to match on the spark interpreter")
	  if (sparkInterpreter.isCompleted) return (sparkInterpreter.value.get.toOption)
	  asScalaBuffer(getInterpreterGroup) foreach {
        case j: WrappedInterpreter => {
          j.getInnerInterpreter match {

            case ed: SparkInterpreter => sparkInterpreterP.success(ed)
            case _ ⇒ {}
          }
        }
        case m: SparkInterpreter => sparkInterpreterP.success(m)
        case _ ⇒ {}
      }

    if (!sparkInterpreterP.isCompleted) {
	    sparkInterpreterP.failure(new InterpreterException("No Spark Interpreter Available"))
    }
    getSparkInterpreter()
  }

  // Update properties based on spark configuration
  private def extractSparkConf(): Unit = sc match {
    case Some(x: SparkContext) => setProperty(x.getConf.getAll.toMap)
    case _ => {}
  }

  def setProperty(properties: Map[String, String]): Unit = property.putAll(properties)
  logger.info("RContext Finished Starting")

}

import scala.concurrent.duration._
object RContext {

	private val logger: Logger = LoggerFactory.getLogger(getClass)

  import scala.sys.process._
  private lazy val timeout : Int = 60

	private val rconInput      = Promise[RContext]()
  private val rcon           = rconInput.future

  def apply(property: Properties) : RContext = synchronized {
	                                                            rcon.isCompleted match {
		                                                            case false => {
			                                                            val debug: Boolean = try { {
				                                                            property.getProperty("rscala.debug", "false")
					                                                            .toBoolean
			                                                            }
			                                                            } catch {
				                                                            case e: Exception ⇒ {
					                                                            logger.info("Exception getting debug status " + 3)
					                                                            true
				                                                            }
			                                                            }
        import scala.sys.process._
        logger.debug("Creating processIO")
        var cmd: PrintWriter = null
        val command = RClient.defaultRCmd +: RClient.defaultArguments
        val processCmd = Process(command)

        val processIO = new ProcessIO(
                                       o => { cmd = new PrintWriter(o) },
                                       reader("STDOUT DEBUG: "),
                                       reader("STDERR DEBUG: "),
                                       true
                                     )
        val portsFile = File.createTempFile("rscala-","")
        val processInstance = processCmd.run(processIO)
        val snippet = s"""
rscala:::rServe(rscala:::newSockets('${portsFile.getAbsolutePath.replaceAll(File.separator,"/")}',debug=${if ( debug ) "TRUE" else "FALSE"},timeout=${timeout}))
q(save='no')
    """
        while ( cmd == null ) Thread.sleep(100)
        logger.debug("sending snippet " + snippet)
        cmd.println(snippet)
        cmd.flush()
        val sockets = new ScalaSockets(portsFile.getAbsolutePath)
        sockets.out.writeInt(OK)
        sockets.out.flush()
        assert( Helper.readString(sockets.in) == org.apache.zeppelin.rinterpreter.rscala.Version )
			                                                            rconInput
				                                                            .success(new RContext(sockets.in, sockets.out))
			                                                            Await.result(rcon, 60 seconds).setProperty(property)
        apply(property)
      }
		                                                            case true => Await.result(rcon, 60 seconds)
    }
  }

}

