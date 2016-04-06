/*
Copyright (c) 2013-2015, David B. Dahl, Brigham Young University

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

    Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in
    the documentation and/or other materials provided with the
    distribution.

    Neither the name of the <ORGANIZATION> nor the names of its
    contributors may be used to endorse or promote products derived
    from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.apache.zeppelin.rinterpreter.rscala

// TODO:  Add libdir to constructor

import java.io._
import java.net.{InetAddress, ServerSocket}

import org.slf4j.{Logger, LoggerFactory}

import scala.language.dynamics

class RClient (private val in: DataInputStream,
               private val out: DataOutputStream,
               val debug: Boolean = true) extends Dynamic {
  var damagedState : Boolean = false
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  case class RObjectRef(val reference : String)  {
    override def toString() = ".$"+reference
  }

  /** __For rscala developers only__: Sets whether debugging output should be displayed. */
  def debug_=(v: Boolean) = {
    if ( v != debug ) {
      if ( debug ) logger.debug("Sending DEBUG request.")
      out.writeInt(RClient.Protocol.DEBUG)
      out.writeInt(if ( v ) 1 else 0)
      out.flush()
    }
  }

  def exit() = {
    logger.debug("Sending EXIT request.")
    out.writeInt(RClient.Protocol.EXIT)
    out.flush()
  }

  def eval(snippet: String, evalOnly: Boolean = true): Any = try {
    if (damagedState) throw new RException("Connection to R already damaged")
    logger.debug("Sending EVAL request.")
    out.writeInt(RClient.Protocol.EVAL)
    RClient.writeString(out,snippet)
    out.flush()
    val status = in.readInt()
    val output = RClient.readString(in)
    if ( output != "" ) {
      logger.error("R Error " + snippet + " " + output)
      throw new RException(snippet, output)
    }
    if ( status != RClient.Protocol.OK ) throw new RException(snippet, output, "Error in R evaluation.")
    if ( evalOnly ) null else get(".rzeppelin.last.value")._1
  } catch {
    case e : java.net.SocketException => {
      logger.error("Connection to R appears to have shut down" + e)
      damagedState = true
    }
  }

  def evalI0(snippet: String) = { eval(snippet,true); getI0(".rzeppelin.last.value") }

  def evalB0(snippet: String) = { eval(snippet,true); getB0(".rzeppelin.last.value") }

  def evalS0(snippet: String) = { eval(snippet,true); getS0(".rzeppelin.last.value") }

  def evalI1(snippet: String) = { eval(snippet,true); getI1(".rzeppelin.last.value") }

  def evalB1(snippet: String) = { eval(snippet,true); getB1(".rzeppelin.last.value") }

  def evalS1(snippet: String) = { eval(snippet,true); getS1(".rzeppelin.last.value") }

  def evalR( snippet: String) = { eval(snippet,true); getR( ".rzeppelin.last.value") }

  def set(identifier: String, value: Any): Unit = set(identifier,value,"",true)

  def set(identifier: String, value: Any, index: String = "", singleBrackets: Boolean = true): Unit = {
    if (damagedState) throw new RException("Connection to R already damaged")
    val v = value
    if ( index == "" ) out.writeInt(RClient.Protocol.SET)
    else if ( singleBrackets ) {
      out.writeInt(RClient.Protocol.SET_SINGLE)
      RClient.writeString(out,index)
    } else {
      out.writeInt(RClient.Protocol.SET_DOUBLE)
      RClient.writeString(out,index)
    }
    RClient.writeString(out,identifier)
    if ( v == null || v.isInstanceOf[Unit] ) {
      logger.debug("... which is null")
      out.writeInt(RClient.Protocol.NULLTYPE)
      out.flush()
      if ( index != "" ) {
        val status = in.readInt()
        if ( status != RClient.Protocol.OK ) {
          val output = RClient.readString(in)
          if ( output != "" ) {
            logger.error("R error setting " + output)
            throw new RException(identifier + value.toString(), output, "Error setting")
          }
          throw new RException("Error in R evaluation. Set " + identifier + " to " + value.toString())
        }
      }
      return
    }
    val c = v.getClass
    logger.debug("... whose class is: "+c)
    logger.debug("... and whose value is: "+v)
    if ( c.isArray ) {
      c.getName match {
        case "[I" =>
          val vv = v.asInstanceOf[Array[Int]]
          out.writeInt(RClient.Protocol.VECTOR)
          out.writeInt(vv.length)
          out.writeInt(RClient.Protocol.INTEGER)
          for ( i <- 0 until vv.length ) out.writeInt(vv(i))
        case "[Z" =>
          val vv = v.asInstanceOf[Array[Boolean]]
          out.writeInt(RClient.Protocol.VECTOR)
          out.writeInt(vv.length)
          out.writeInt(RClient.Protocol.BOOLEAN)
          for ( i <- 0 until vv.length ) out.writeInt(if ( vv(i) ) 1 else 0)
        case "[Ljava.lang.String;" =>
          val vv = v.asInstanceOf[Array[String]]
          out.writeInt(RClient.Protocol.VECTOR)
          out.writeInt(vv.length)
          out.writeInt(RClient.Protocol.STRING)
          for ( i <- 0 until vv.length ) RClient.writeString(out,vv(i))
        case _ =>
          throw new RException("Unsupported array type: "+c.getName)
      }
    } else {
      c.getName match {
        case "java.lang.Integer" =>
          out.writeInt(RClient.Protocol.ATOMIC)
          out.writeInt(RClient.Protocol.INTEGER)
          out.writeInt(v.asInstanceOf[Int])
        case "java.lang.Boolean" =>
          out.writeInt(RClient.Protocol.ATOMIC)
          out.writeInt(RClient.Protocol.BOOLEAN)
          out.writeInt(if (v.asInstanceOf[Boolean]) 1 else 0)
        case "java.lang.String" =>
          out.writeInt(RClient.Protocol.ATOMIC)
          out.writeInt(RClient.Protocol.STRING)
          RClient.writeString(out,v.asInstanceOf[String])
        case _ =>
          throw new RException("Unsupported non-array type: "+c.getName)
      }
    }
    out.flush()
    if ( index != "" ) {
      val status = in.readInt()
      if ( status != RClient.Protocol.OK ) {
        val output = RClient.readString(in)
        if ( output != "" ) throw new RException(identifier + value.toString(), output, "Error setting")
        throw new RException("Error in R evaluation.")
      }
    }
  }

  def get(identifier: String, asReference: Boolean = false): (Any,String) = {
    logger.debug("Getting: "+identifier)
    out.writeInt(if ( asReference ) RClient.Protocol.GET_REFERENCE else RClient.Protocol.GET)
    RClient.writeString(out,identifier)
    out.flush()
    if ( asReference ) {
      val r = in.readInt() match {
        case RClient.Protocol.REFERENCE => (RObjectRef(RClient.readString(in)),"RObject")
        case RClient.Protocol.UNDEFINED_IDENTIFIER =>
          throw new RException("Undefined identifier")
      }
      return r
    }
    in.readInt match {
      case RClient.Protocol.NULLTYPE =>
        logger.debug("Getting null.")
        (null,"Null")
      case RClient.Protocol.ATOMIC =>
        logger.debug("Getting atomic.")
        in.readInt() match {
          case RClient.Protocol.INTEGER => (in.readInt(),"Int")
          case RClient.Protocol.DOUBLE => (in.readDouble(),"Double")
          case RClient.Protocol.BOOLEAN => (( in.readInt() != 0 ),"Boolean")
          case RClient.Protocol.STRING => (RClient.readString(in),"String")
          case _ => throw new RException("Protocol error")
        }
      case RClient.Protocol.VECTOR =>
        logger.debug("Getting vector...")
        val length = in.readInt()
        logger.debug("... of length: "+length)
        in.readInt() match {
          case RClient.Protocol.INTEGER => (Array.fill(length) { in.readInt() },"Array[Int]")
          case RClient.Protocol.DOUBLE => (Array.fill(length) { in.readDouble() },"Array[Double]")
          case RClient.Protocol.BOOLEAN => (Array.fill(length) { ( in.readInt() != 0 ) },"Array[Boolean]")
          case RClient.Protocol.STRING => (Array.fill(length) { RClient.readString(in) },"Array[String]")
          case _ => throw new RException("Protocol error")
        }
      case RClient.Protocol.MATRIX =>
        logger.debug("Getting matrix...")
        val nrow = in.readInt()
        val ncol = in.readInt()
        logger.debug("... of dimensions: "+nrow+","+ncol)
        in.readInt() match {
          case RClient.Protocol.INTEGER => (Array.fill(nrow) { Array.fill(ncol) { in.readInt() } },"Array[Array[Int]]")
          case RClient.Protocol.DOUBLE => (Array.fill(nrow) { Array.fill(ncol) { in.readDouble() } },"Array[Array[Double]]")
          case RClient.Protocol.BOOLEAN => (Array.fill(nrow) { Array.fill(ncol) { ( in.readInt() != 0 ) } },"Array[Array[Boolean]]")
          case RClient.Protocol.STRING => (Array.fill(nrow) { Array.fill(ncol) { RClient.readString(in) } },"Array[Array[String]]")
          case _ => throw new RException("Protocol error")
        }
      case RClient.Protocol.UNDEFINED_IDENTIFIER => throw new RException("Undefined identifier")
      case RClient.Protocol.UNSUPPORTED_STRUCTURE => throw new RException("Unsupported data type")
      case _ => throw new RException("Protocol error")
    }
  }

  def getI0(identifier: String): Int = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int]
    case (a,"Double") => a.asInstanceOf[Double].toInt
    case (a,"Boolean") => if (a.asInstanceOf[Boolean]) 1 else 0
    case (a,"String") => a.asInstanceOf[String].toInt
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0)
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]](0).toInt
    case (a,"Array[Boolean]") => if ( a.asInstanceOf[Array[Boolean]](0) ) 1 else 0
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0).toInt
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to Int")
  }

  def getD0(identifier: String): Double = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int].toDouble
    case (a,"Double") => a.asInstanceOf[Double]
    case (a,"Boolean") => if (a.asInstanceOf[Boolean]) 1.0 else 0.0
    case (a,"String") => a.asInstanceOf[String].toDouble
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0).toDouble
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]](0)
    case (a,"Array[Boolean]") => if ( a.asInstanceOf[Array[Boolean]](0) ) 1.0 else 0.0
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0).toDouble
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to Double")
  }

  def getB0(identifier: String): Boolean = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int] != 0
    case (a,"Boolean") => a.asInstanceOf[Boolean]
    case (a,"String") => a.asInstanceOf[String].toLowerCase != "false"
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0) != 0
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]](0)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0).toLowerCase != "false"
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to Boolean")
  }

  def getS0(identifier: String): String = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int].toString
    case (a,"Boolean") => a.asInstanceOf[Boolean].toString
    case (a,"String") => a.asInstanceOf[String]
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0).toString
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]](0).toString
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0)
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to String")
  }

  def getI1(identifier: String): Array[Int] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int])
    case (a,"Boolean") => Array(if (a.asInstanceOf[Boolean]) 1 else 0)
    case (a,"String") => Array(a.asInstanceOf[String].toInt)
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]]
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]].map(x => if (x) 1 else 0)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]].map(_.toInt)
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to Array[Int]")
  }

  def getB1(identifier: String): Array[Boolean] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int] != 0)
    case (a,"Boolean") => Array(a.asInstanceOf[Boolean])
    case (a,"String") => Array(a.asInstanceOf[String].toLowerCase != "false")
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]].map(_ != 0)
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]]
    case (a,"Array[String]") => a.asInstanceOf[Array[String]].map(_.toLowerCase != "false")
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to Array[Boolean]")
  }

  def getS1(identifier: String): Array[String] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int].toString)
    case (a,"Boolean") => Array(a.asInstanceOf[Boolean].toString)
    case (a,"String") => Array(a.asInstanceOf[String])
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]].map(_.toString)
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]].map(_.toString)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]]
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to Array[String]")
  }

  def getR(identifier: String): RObjectRef = get(identifier,true) match {
    case (a,"RObject") => a.asInstanceOf[RObjectRef]
    case (_,tp) => throw new RException(s"Unable to cast ${tp} to RObject")
  }

  def gc(): Unit = {
    logger.debug("Sending GC request.")
    out.writeInt(RClient.Protocol.GC)
    out.flush()
  }



}

object RClient {

  object Protocol {

    // Data Types
    val UNSUPPORTED_TYPE = 0
    val INTEGER = 1
    val DOUBLE =  2
    val BOOLEAN = 3
    val STRING =  4
    val DATE = 5
    val DATETIME = 6

    // Data Structures
    val UNSUPPORTED_STRUCTURE = 10
    val NULLTYPE  = 11
    val REFERENCE = 12
    val ATOMIC    = 13
    val VECTOR    = 14
    val MATRIX    = 15
    val LIST      = 16
    val DATAFRAME = 17
    val S3CLASS   = 18
    val S4CLASS   = 19
    val JOBJ      = 20

    // Commands
    val EXIT          = 100
    val RESET         = 101
    val GC            = 102
    val DEBUG         = 103
    val EVAL          = 104
    val SET           = 105
    val SET_SINGLE    = 106
    val SET_DOUBLE    = 107
    val GET           = 108
    val GET_REFERENCE = 109
    val DEF           = 110
    val INVOKE        = 111
    val SCALAP        = 112

    // Result
    val OK = 1000
    val ERROR = 1001
    val UNDEFINED_IDENTIFIER = 1002

    // Misc.
    val CURRENT_SUPPORTED_SCALA_VERSION = "2.10"

  }

  def writeString(out: DataOutputStream, string: String): Unit = {
    val bytes = string.getBytes("UTF-8")
    val length = bytes.length
    out.writeInt(length)
    out.write(bytes,0,length)
  }

  def readString(in: DataInputStream): String = {
    val length = in.readInt()
    val bytes = new Array[Byte](length)
    in.readFully(bytes)
    new String(bytes,"UTF-8")
  }

  def isMatrix[T](x: Array[Array[T]]): Boolean = {
    if ( x.length != 0 ) {
      val len = x(0).length
      for ( i <- 1 until x.length ) {
        if ( x(i).length != len ) return false
      }
    }
    true
  }

  import scala.sys.process._
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  val OS = sys.props("os.name").toLowerCase match {
    case s if s.startsWith("""windows""") => "windows"
    case s if s.startsWith("""linux""") => "linux"
    case s if s.startsWith("""unix""") => "linux"
    case s if s.startsWith("""mac""") => "macintosh"
    case _ => throw new RException("Unrecognized OS")
  }

  val defaultArguments = OS match {
    case "windows" =>    Array[String]("--vanilla","--silent","--slave","--ess")
    case "linux" =>      Array[String]("--vanilla","--silent","--slave","--interactive")
    case "unix" =>       Array[String]("--vanilla","--silent","--slave","--interactive")
    case "macintosh" =>  Array[String]("--vanilla","--silent","--slave","--interactive")
  }

  lazy val defaultRCmd = OS match {
    case "windows" =>   findROnWindows
    case "linux" =>     """R"""
    case "unix" =>      """R"""
    case "macintosh" => """R"""
  }

  def findROnWindows: String = {
    val NEWLINE = sys.props("line.separator")
    var result : String = null
    for ( root <- List("HKEY_LOCAL_MACHINE","HKEY_CURRENT_USER") ) {
      val out = new StringBuilder()
      val logger = ProcessLogger((o: String) => { out.append(o); out.append(NEWLINE) },(e: String) => {})
      try {
        ("reg query \"" + root + "\\Software\\R-core\\R\" /v \"InstallPath\"") ! logger
        val a = out.toString.split(NEWLINE).filter(_.matches("""^\s*InstallPath\s*.*"""))(0)
        result = a.split("REG_SZ")(1).trim() + """\bin\R.exe"""
      } catch {
        case _ : Throwable =>
      }
    }
    if ( result == null ) throw new RException("Cannot locate R using Windows registry.")
    else return result
  }

  def reader(label: String)(input: InputStream) = {
    val in = new BufferedReader(new InputStreamReader(input))
    var line = in.readLine()
    while ( line != null ) {
      logger.debug(label+line)
      line = in.readLine()
    }
    in.close()
  }

  class ScalaSockets(portsFilename: String) {
    private val logger: Logger = LoggerFactory.getLogger(getClass)

    val serverIn  = new ServerSocket(0,0,InetAddress.getByName(null))
    val serverOut = new ServerSocket(0,0,InetAddress.getByName(null))

    locally {
      logger.info("Trying to open ports filename: "+portsFilename)
      val portNumberFile = new File(portsFilename)
      val p = new PrintWriter(portNumberFile)
      p.println(serverIn.getLocalPort+" "+serverOut.getLocalPort)
      p.close()
      logger.info("Servers are running on port "+serverIn.getLocalPort+" "+serverOut.getLocalPort)
    }

    val socketIn = serverIn.accept
    logger.info("serverinaccept done")
    val in = new DataInputStream(new BufferedInputStream(socketIn.getInputStream))
    logger.info("in has been created")
    val socketOut = serverOut.accept
    logger.info("serverouacceptdone")
    val out = new DataOutputStream(new BufferedOutputStream(socketOut.getOutputStream))
    logger.info("out is done")
  }

  def makeSockets(portsFilename : String) = new ScalaSockets(portsFilename)

  def apply(): RClient = apply(defaultRCmd)

  def apply(rCmd: String, libdir : String = "",debug: Boolean = false, timeout: Int = 60): RClient = {
    logger.debug("Creating processIO")
    var cmd: PrintWriter = null
    val command = rCmd +: defaultArguments
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
    logger.info("sending snippet " + snippet)
    cmd.println(snippet)
    cmd.flush()
    val sockets = makeSockets(portsFile.getAbsolutePath)
    sockets.out.writeInt(Protocol.OK)
    sockets.out.flush()
    try {
      assert( readString(sockets.in) == org.apache.zeppelin.rinterpreter.rscala.Version )
    } catch {
      case _: Throwable => throw new RException("The scala and R versions of the package don't match")
    }
    apply(sockets.in,sockets.out)
  }

  /** __For rscala developers only__: Returns an instance of the [[RClient]] class.  */
  def apply(in: DataInputStream, out: DataOutputStream): RClient = new RClient(in,out)

}