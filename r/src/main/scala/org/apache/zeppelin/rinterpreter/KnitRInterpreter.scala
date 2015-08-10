package org.apache.zeppelin.rinterpreter


// TODO:  Capture the knitr progress bar

import java.util._

import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterException, InterpreterResult}
import org.apache.zeppelin.rinterpreter.rscala.RException
import org.slf4j.{Logger, LoggerFactory}

class KnitRInterpreter (property: Properties) extends RInterpreter (property) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  logger.info("Starting KnitR")


  def this() = {
    this(new Properties())
  }

  override def open: Unit = {
    logger.info("Opening")
    rContext.synchronized {
                   super.open
                   logger.info("Knitr open, initial commands")
                   if (!rContext.evalB0("require(knitr)")) throw
                     new InterpreterException(
       """
         |The knitr package could not be loaded.  We cannot install it
         |for you because it is published under the GPL3 license.  You can
         |install it from CRAN with install.packages("knitr")
       """.stripMargin)
                            rContext.eval( """opts_knit$set(out.format = 'html',
                           |results='asis',
                           |progress = FALSE,
                           |self.contained = TRUE,
                           |verbose = FALSE,
                           |tidy = TRUE)""".stripMargin)
                 }
    logger.info("KnitR:  Finished initial commands")
  }

  def interpret(st: String, context: InterpreterContext): InterpreterResult = try { {
    logger.debug("interpreting" + st)
    // need to convert st into an array of Strings within R
    rContext.set(".zeppknitrinput", st.split("\n"))
    val out = rContext.synchronized {
                                      rContext
                                        .eval(".knitout <- knit2html(text=.zeppknitrinput, envir = .zeppenv)")
                                      rContext.updateZeppelinContext()
                                      rContext.getS1(".knitout") // TEST: Should this be S1 or S0?
                                    }
    logger.debug("Assemble  output")

    new InterpreterResult(InterpreterResult.Code.SUCCESS,
                          InterpreterResult.Type.HTML,
                          RInterpreter.processHTML(out.mkString("\n"))
    )
  }
  } catch {
    case r: RException => r.getInterpreterResult(st)
    case e: Exception => new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage())
  }
}

