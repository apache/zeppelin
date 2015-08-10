package org.apache.zeppelin.rinterpreter.rscala

import java.io._
import java.net._

import org.slf4j.{Logger, LoggerFactory}

private[rinterpreter] class ScalaSockets(portsFilename: String) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val serverIn  = new ServerSocket(0,0,InetAddress.getByName(null))
  val serverOut = new ServerSocket(0,0,InetAddress.getByName(null))

  locally {
    logger.debug("Trying to open ports filename: "+portsFilename)
    val portNumberFile = new File(portsFilename)
    val p = new PrintWriter(portNumberFile)
    p.println(serverIn.getLocalPort+" "+serverOut.getLocalPort)
    p.close()
    logger.debug("Servers are running on port "+serverIn.getLocalPort+" "+serverOut.getLocalPort)
  }

  val socketIn = serverIn.accept
  val in = new DataInputStream(new BufferedInputStream(socketIn.getInputStream))
  val socketOut = serverOut.accept
  val out = new DataOutputStream(new BufferedOutputStream(socketOut.getOutputStream))

}

