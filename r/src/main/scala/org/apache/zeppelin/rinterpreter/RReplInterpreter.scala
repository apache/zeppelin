package org.apache.zeppelin.rinterpreter

// options for getting data include the repr package and the evaluate() command
// TODO:  Option for setting size of output images

import java.util._

import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterResult}
import org.apache.zeppelin.rinterpreter.rscala.RException
import org.slf4j.{Logger, LoggerFactory}

class RReplInterpreter (property: Properties) extends RInterpreter(property) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  logger.info("Initialising an RInterpreter of class " + this.getClass.getName)

  def this() = {
    this(new Properties())
  }
  def interpret(st: String, context: InterpreterContext): InterpreterResult = try { {
    import scala.collection.immutable._
    logger.debug("intrpreting " + st)
    rContext.set(".zeppcmdrinput", st.split("\n"))
    rContext.eval(
      """
        |.zeppcmdroutput <- evaluate(
        |input = .zeppcmdrinput,
        |envir = .zeppenv,
        |debug = FALSE,
        |output_handler = .zohandler
        |)
      """.stripMargin)
    rContext.updateZeppelinContext()
    val reslength: Int = rContext.evalI0("length(.zeppcmdroutput)")
    logger.debug("Length of evaluate result is " + reslength)
    val resclasses: List[String] = List.range(1, reslength + 1)
      .map((i: Int) => rContext.evalS0(s"class(.zeppcmdroutput[[${i}]])"))
    logger.info("Found these classes: " + resclasses.mkString(" "))
    // Zeppelin only allows one magic per result.  So if there's a data.frame or image, that will be the only result.
    // TODO: Handle other types that dominate the result, like html or angular?
    resclasses.indexOf("recordedplot") + 1 match {
      case 0 => {}
      case x: Int => {
        logger.info("Found a recorded plot")
        //        val image : String = rContext.evalS0(s"repr_svg(.zeppcmdroutput[[${x}]])")
        val image: String = rContext.evalS0(s"base64encode(repr_jpg(.zeppcmdroutput[[${x}]]))")
        logger.debug("Got " + image)
        return new InterpreterResult(InterpreterResult.Code.SUCCESS,
                                     InterpreterResult.Type.IMG, image)
        //                                     InterpreterResult.Type.SVG,
        //                                     image.substring(image.indexOf("<svg") - 1, image.length))
      }
    }
    logger.info("its not a recorded plot")
    resclasses.indexOf("data.frame") + 1 match {
      case 0 => {}
      case x: Int => {
        logger.info("Found a data.frame")
        rContext.eval(
          s"""
             |.zdfoutcon <- textConnection(".zdfout", open="w")
             |write.table(.zeppcmdroutput[[${x}
]],
             |   col.names=TRUE, row.names=FALSE, sep="\t",
             |   eol="\n", quote = FALSE, file = .zdfoutcon)
          """.stripMargin)
        val table: Array[String] = rContext
          .getS1(".zdfout") // cleanup - otherwise the connection stays open and future attempts append
        rContext.eval("""
            |rm(.zdfoutcon)
            |rm(.zdfout)
          """.stripMargin)
        return new InterpreterResult(InterpreterResult.Code.SUCCESS,
                                     InterpreterResult.Type.TABLE,
                                     table.mkString(sep = "\n"))
      }
    } // Now just read-out the elements in sequence like a repl session
    val zipped: List[(Int, String)] = List.range(1, reslength + 1).zip(resclasses)
    logger.debug("Found " + zipped)
    val normalout: String = zipped.map((in: (Int, String))
                                       => in._2 match {
        case "source" => rContext.evalS0(s".zeppcmdroutput[[${in._1}]]" + "$src")
        case "character" => rContext.
          evalS0(s".zeppcmdroutput[[${in._1}]]")
        // FIXME:  Right now returning with repr doesn't quite work, because the default output handler for evaluate calls print() or show() on anything other than a data.frame, so there's nothing for repr to operate on
        case _ => rContext.
          evalS0(s"repr(.zeppcmdroutput[[${in._1}]])")
      }
    ).mkString("\n\n")
    return new InterpreterResult(
      InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TEXT, normalout)
  }
  } catch {
    case re : RException => return re.getInterpreterResult(st)
    case e: Exception => {
      logger.error("Error interpreting " + st, e)
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage() + e.getStackTrace)
    }
  }




  override def open: Unit = {
    logger.info("Opening")
    rContext.synchronized {
                            super.open
                            logger.info("RRepl open, initial commands")
                            testPackage("evaluate", fail = true, license = true)
                            rContext.eval(
                              """
                                |.zohandler = new_output_handler(
                                | value = function(x) {
                                |   if (is.data.frame(x)) return(x)
                                |   if (isS4(x)) show(x) else print(x)
                                |  }
                                |)
                              """.stripMargin)
                            testPackage("repr", license = true)
                          }
    testPackage("base64enc", fail = true, license = true)

    logger.info("RRepl:  Finished initial commands")
  }
}
