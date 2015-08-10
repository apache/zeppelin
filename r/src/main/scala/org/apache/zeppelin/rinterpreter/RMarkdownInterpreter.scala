package org.apache.zeppelin.rinterpreter

// FIXME:  The fragment isn't apparently including its javascript, so try it with html and trim.

// TODO:  Set rmarkdown defaults, like html theme.
// TODO:  Allow CSS
// TODO:  Is it possible to get the render without temp files?
// FIXME: Interactive visualizations not running

import java.io.PrintWriter
import java.util.Properties

import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterResult}
import org.apache.zeppelin.rinterpreter.rscala.RException
import org.slf4j.{Logger, LoggerFactory}

/**
 * Created by aelberg on 8/5/15.
 */
class RMarkdownInterpreter(property: Properties) extends RInterpreter(property) {

	private val logger: Logger = LoggerFactory.getLogger(getClass)
	logger.info("Starting RMarkdown")

	def this() = {
		this(new Properties())
	}

	override def open: Unit = rContext.synchronized {
      super.open
      logger.info("rmarkdown open, initial commands")
      testPackage("rmarkdown", license = true, fail = true)
      // TEST:  Not sure if these options are getting picked-up properly
        rContext.eval("""opts_knit$set(out.format = 'html',
               |results='asis',
               |progress = FALSE,
               |self.contained = TRUE,
               |verbose = FALSE)
               |
               |.zepphtml <- html_fragment (
               |  fig_width = 7,
               |  fig_height = 5,
               |  fig_caption = TRUE,
               |  self_contained=TRUE
               |)
               |
               |.zepprender <- function(infile) {
               |  render(infile,
               |    output_format = .zepphtml,
               |    output_file = tempfile(),
               |    intermediates_dir = tempdir(),
               |    runtime = 'static',
               |    clean = TRUE,
               |    quiet = TRUE,
               |    envir = .zeppenv)
               |}""".stripMargin)
               logger.info("RMarkdown:  Finished initial commands")
     }

	// TODO:  This can either be html_document or html_fragment.  Currently fragment for testing.  If can handle visualizations, would be better as fragment

	 def interpret(st: String, context:
	InterpreterContext): InterpreterResult = {
		logger.debug("interpreting" + st)
		val infile = java.io.File.createTempFile("zepp", ".Rmd")
		val pw = new PrintWriter(infile)
		pw.write(st)
		pw.close()
		var pathout: String = null
		this.synchronized {
			                  try {
				                  // need to convert st into an array of Strings within R {
				                  rContext.set(".zinfile", infile.getAbsolutePath)
				                  rContext.eval(".zout <- .zepprender(.zinfile)")
				                  pathout = rContext.getS0(".zout")
				                  rContext.updateZeppelinContext()
			                  } catch {
				                  case
					                  r: RException => return r.getInterpreterResult
				                  case e: Exception => return new
						                  InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage())
			                  }
		                  }

		val htmlout: String = scala.io.Source.fromFile(pathout).getLines().mkString("\n")
		logger.debug("Assemble  output")
		logger.debug("RMARKDOWN OUTPUT: " + htmlout)
		new InterpreterResult(InterpreterResult.Code.SUCCESS,
		                      InterpreterResult.Type.HTML,
		                      htmlout
		)
  }

}