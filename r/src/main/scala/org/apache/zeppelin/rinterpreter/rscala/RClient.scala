package org.apache.zeppelin.rinterpreter.rscala

// TODO:  Add libdir to constructor

import java.io._

import Protocol._
import org.slf4j.{Logger, LoggerFactory}

import scala.language.dynamics

/** An interface to an R interpreter.
*
* An object `R` is the instance of this class available in a Scala interpreter created by calling the function
* `scalaInterpreter` from the package [[http://cran.r-project.org/package=rscala rscala]].  It is through this instance `R` that
* callbacks to the original [[http://www.r-project.org R]] interpreter are possible.
* 
* In a JVM-based application, an instance of this class is created using its companion object.  See below.  The paths of the
* rscala's JARs, for both Scala 2.10 and 2.11, are available from [[http://www.r-project.org R]] using `rscala::rscalaJar()`.
* To get just the JAR for Scala 2.11, for example, use `rscala::rscalaJar("2.11")`.
* 
* {{{
* val R = RClient()

* val a = R.evalD0("rnorm(8)")
* val b = R.evalD1("rnorm(8)")
* val c = R.evalD2("matrix(rnorm(8),nrow=4)")

* R eval """
*   v <- rbinom(8,size=10,prob=0.4)
*   m <- matrix(v,nrow=4)
* """
* val v1 = R.get("v")
* val v2 = R.get("v")._1.asInstanceOf[Array[Int]]   // This works, but is not very convenient
* val v3 = R.v._1.asInstanceOf[Array[Int]]          // Slightly better
* val v4 = R.getI0("v")   // Get the first element of R's "v" as a Int
* val v5 = R.getI1("v")   // Get R's "v" as an Array[Int]
* val v6 = R.getI2("m")   // Get R's "m" as an Array[Array[Int]]
* }}}
*
* @author David B. Dahl
*/
class RClient (private val in: DataInputStream,
                       private val out: DataOutputStream,
               val debug: Boolean = true) extends Dynamic {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /** __For rscala developers only__: Sets whether debugging output should be displayed. */
  def debug_=(v: Boolean) = {
    if ( v != debug ) {
      if ( debug ) logger.debug("Sending DEBUG request.")
      out.writeInt(DEBUG)
      out.writeInt(if ( v ) 1 else 0)
      out.flush()
    }
  }

  /** Closes the interface to the R interpreter.
  * 
  * Subsequent calls to the other methods will fail.
  */
  def exit() = {
    logger.debug("Sending EXIT request.")
    out.writeInt(EXIT)
    out.flush()
  }

  /** Evaluates `snippet` in the R interpreter.
  *
  * Returns `null` if `evalOnly`.  If `!evalOnly`, the last result of the R expression is converted if possible.
  * Conversion to integers, doubles, Booleans, and strings are supported, as are vectors (i.e. arrays) and matrices
  * (i.e. retangular arrays of arrays) of these types.  The static type of the result, however, is `Any` so using the
  * method `evalXY` (where `X` is `I`, `D`, `B`, or `S` and `Y` is `0`, `1`, or `2`) may be more convenient (e.g.
  * [[evalD0]]).
  */
  def eval(snippet: String, evalOnly: Boolean = true): Any = {
    logger.debug("Sending EVAL request.")
    out.writeInt(EVAL)
    Helper.writeString(out,snippet)
    out.flush()
    val status = in.readInt()
    val output = Helper.readString(in)
    if ( output != "" ) {
      logger.error("R Error " + snippet + " " + output)
      throw new RException(snippet, output)
    }
    if ( status != OK ) throw new RException(snippet, output, "Error in R evaluation.")
    if ( evalOnly ) null else get(".rscala.last.value")._1
  }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getI0]].  */
  def evalI0(snippet: String) = { eval(snippet,true); getI0(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getD0]].  */
  def evalD0(snippet: String) = { eval(snippet,true); getD0(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getB0]].  */
  def evalB0(snippet: String) = { eval(snippet,true); getB0(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getS0]].  */
  def evalS0(snippet: String) = { eval(snippet,true); getS0(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getI1]].  */
  def evalI1(snippet: String) = { eval(snippet,true); getI1(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getD1]].  */
  def evalD1(snippet: String) = { eval(snippet,true); getD1(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getB1]].  */
  def evalB1(snippet: String) = { eval(snippet,true); getB1(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getS1]].  */
  def evalS1(snippet: String) = { eval(snippet,true); getS1(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getI2]].  */
  def evalI2(snippet: String) = { eval(snippet,true); getI2(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getD2]].  */
  def evalD2(snippet: String) = { eval(snippet,true); getD2(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getB2]].  */
  def evalB2(snippet: String) = { eval(snippet,true); getB2(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getS2]].  */
  def evalS2(snippet: String) = { eval(snippet,true); getS2(".rscala.last.value") }

  /** Calls '''`eval(snippet,true)`''' and returns the result using [[getR]].  */
  def evalR( snippet: String) = { eval(snippet,true); getR( ".rscala.last.value") }


  /** Equivalent to calling '''`set(identifier, value, "", true)`'''. */
  def set(identifier: String, value: Any): Unit = set(identifier,value,"",true)

  /** Assigns `value` to a variable `identifier` in the R interpreter.
  * 
  * Integers, doubles, Booleans, and strings are supported, as are vectors (i.e. arrays) and matrices
  * (i.e. retangular arrays of arrays) of these types.
  * 
  * If `index != ""`, assigned into elements of `identifier` are performed by using either single brackets
  * (`singleBrackets=true`) or double brackets (`singleBrackets=false`).
  *
  * {{{
  * R.a = Array(5,6,4)
  *
  * R.eval("b <- matrix(NA,nrow=3,ncol=2)")
  * for ( i <- 0 until 3 ) {
  *   R.set("b",Array(2*i,2*i+1),s"\${i+1},")
  * }
  * R.b
  *
  * R.eval("myList <- list()")
  * R.set("myList",Array("David","Grace","Susan"),"'names'",false)
  * R.set("myList",Array(5,4,5),"'counts'",false)
  * R.eval("print(myList)")
  * }}}
  */
  def set(identifier: String, value: Any, index: String = "", singleBrackets: Boolean = true): Unit = {
    logger.debug("Setting: "+identifier)
    val v = value
    if ( index == "" ) out.writeInt(SET)
    else if ( singleBrackets ) {
      out.writeInt(SET_SINGLE)
      Helper.writeString(out,index)
    } else {
      out.writeInt(SET_DOUBLE)
      Helper.writeString(out,index)
    }
    Helper.writeString(out,identifier)
    if ( v == null || v.isInstanceOf[Unit] ) {
      logger.debug("... which is null")
      out.writeInt(NULLTYPE)
      out.flush()
      if ( index != "" ) {
        val status = in.readInt()
        if ( status != OK ) {
          val output = Helper.readString(in)
          if ( output != "" ) {
            logger.error("R error setting " + output)
            throw new RException(identifier + value.toString(), output, "Error setting")
          }
          throw new RuntimeException("Error in R evaluation. Set " + identifier + " to " + value.toString())
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
          out.writeInt(VECTOR)
          out.writeInt(vv.length)
          out.writeInt(INTEGER)
          for ( i <- 0 until vv.length ) out.writeInt(vv(i))
        case "[D" =>
          val vv = v.asInstanceOf[Array[Double]]
          out.writeInt(VECTOR)
          out.writeInt(vv.length)
          out.writeInt(DOUBLE)
          for ( i <- 0 until vv.length ) out.writeDouble(vv(i))
        case "[Z" =>
          val vv = v.asInstanceOf[Array[Boolean]]
          out.writeInt(VECTOR)
          out.writeInt(vv.length)
          out.writeInt(BOOLEAN)
          for ( i <- 0 until vv.length ) out.writeInt(if ( vv(i) ) 1 else 0)
        case "[Ljava.lang.String;" =>
          val vv = v.asInstanceOf[Array[String]]
          out.writeInt(VECTOR)
          out.writeInt(vv.length)
          out.writeInt(STRING)
          for ( i <- 0 until vv.length ) Helper.writeString(out,vv(i))
        case "[[I" =>
          val vv = v.asInstanceOf[Array[Array[Int]]]
          if ( Helper.isMatrix(vv) ) {
            out.writeInt(MATRIX)
            out.writeInt(vv.length)
            if ( vv.length > 0 ) out.writeInt(vv(0).length)
            else out.writeInt(0)
            out.writeInt(INTEGER)
            for ( i <- 0 until vv.length ) {
              val vvv = vv(i)
              for ( j <- 0 until vvv.length ) {
                out.writeInt(vv(i)(j))
              }
            }
          }
        case "[[D" =>
          val vv = v.asInstanceOf[Array[Array[Double]]]
          if ( Helper.isMatrix(vv) ) {
            out.writeInt(MATRIX)
            out.writeInt(vv.length)
            if ( vv.length > 0 ) out.writeInt(vv(0).length)
            else out.writeInt(0)
            out.writeInt(DOUBLE)
            for ( i <- 0 until vv.length ) {
              val vvv = vv(i)
              for ( j <- 0 until vvv.length ) {
                out.writeDouble(vvv(j))
              }
            }
          } else out.writeInt(UNSUPPORTED_STRUCTURE)
        case "[[Z" =>
          val vv = v.asInstanceOf[Array[Array[Boolean]]]
          if ( Helper.isMatrix(vv) ) {
            out.writeInt(MATRIX)
            out.writeInt(vv.length)
            if ( vv.length > 0 ) out.writeInt(vv(0).length)
            else out.writeInt(0)
            out.writeInt(BOOLEAN)
            for ( i <- 0 until vv.length ) {
              val vvv = vv(i)
              for ( j <- 0 until vv(i).length ) {
                out.writeInt(if ( vvv(j) ) 1 else 0)
              }
            }
          } else out.writeInt(UNSUPPORTED_STRUCTURE)
        case "[[Ljava.lang.String;" =>
          val vv = v.asInstanceOf[Array[Array[String]]]
          if ( Helper.isMatrix(vv) ) {
            out.writeInt(MATRIX)
            out.writeInt(vv.length)
            if ( vv.length > 0 ) out.writeInt(vv(0).length)
            else out.writeInt(0)
            out.writeInt(STRING)
            for ( i <- 0 until vv.length ) {
              val vvv = vv(i)
              for ( j <- 0 until vv(i).length ) {
                Helper.writeString(out,vvv(j))
              }
            }
          } else out.writeInt(UNSUPPORTED_STRUCTURE)
        case _ =>
          throw new RuntimeException("Unsupported array type: "+c.getName)
      }
    } else {
      c.getName match {
        case "java.lang.Integer" =>
          out.writeInt(ATOMIC)
          out.writeInt(INTEGER)
          out.writeInt(v.asInstanceOf[Int])
        case "java.lang.Double" =>
          out.writeInt(ATOMIC)
          out.writeInt(DOUBLE)
          out.writeDouble(v.asInstanceOf[Double])
        case "java.lang.Boolean" =>
          out.writeInt(ATOMIC)
          out.writeInt(BOOLEAN)
          out.writeInt(if (v.asInstanceOf[Boolean]) 1 else 0)
        case "java.lang.String" =>
          out.writeInt(ATOMIC)
          out.writeInt(STRING)
          Helper.writeString(out,v.asInstanceOf[String])
        case _ =>
          throw new RuntimeException("Unsupported non-array type: "+c.getName)
      }
    }
    out.flush()
    if ( index != "" ) {
      val status = in.readInt()
      if ( status != OK ) {
        val output = Helper.readString(in)
        if ( output != "" ) throw new RException(identifier + value.toString(), output, "Error setting")
        throw new RuntimeException("Error in R evaluation.")
      }
    }
  }

  /** Returns the value of `identifier` in the R interpreter.  The static type of the result is `(Any,String)`, where
  * the first element is the value and the second is the runtime type.
  *
  * If `asReference=false`, conversion to integers, doubles, Booleans, and strings are supported, as are vectors (i.e.
  * arrays) and matrices (i.e. retangular arrays of arrays) of these types.    Using the method `getXY` (where `X` is
  * `I`, `D`, `B`, or `S` and `Y` is `0`, `1`, or `2`) may be more convenient (e.g.  [[getD0]]).
  *
  * If `asReference=true`, the value is merely wrapped using [[RObjectRef]] and objects of any type are supported.  Using
  * the method [[getR]] may be more convenient.
  */
  def get(identifier: String, asReference: Boolean = false): (Any,String) = {
    logger.debug("Getting: "+identifier)
    out.writeInt(if ( asReference ) GET_REFERENCE else GET)
    Helper.writeString(out,identifier)
    out.flush()
    if ( asReference ) {
      val r = in.readInt() match {
        case REFERENCE => (RObjectRef(Helper.readString(in)),"RObject")
        case UNDEFINED_IDENTIFIER =>
          throw new RuntimeException("Undefined identifier")
      }
      return r
    }
    in.readInt match {
      case NULLTYPE =>
        logger.debug("Getting null.")
        (null,"Null")
      case ATOMIC =>
        logger.debug("Getting atomic.")
        in.readInt() match {
          case INTEGER => (in.readInt(),"Int")
          case DOUBLE => (in.readDouble(),"Double")
          case BOOLEAN => (( in.readInt() != 0 ),"Boolean")
          case STRING => (Helper.readString(in),"String")
          case _ => throw new RuntimeException("Protocol error")
        }
      case VECTOR =>
        logger.debug("Getting vector...")
        val length = in.readInt()
        logger.debug("... of length: "+length)
        in.readInt() match {
          case INTEGER => (Array.fill(length) { in.readInt() },"Array[Int]")
          case DOUBLE => (Array.fill(length) { in.readDouble() },"Array[Double]")
          case BOOLEAN => (Array.fill(length) { ( in.readInt() != 0 ) },"Array[Boolean]")
          case STRING => (Array.fill(length) { Helper.readString(in) },"Array[String]")
          case _ => throw new RuntimeException("Protocol error")
        }
      case MATRIX =>
        logger.debug("Getting matrix...")
        val nrow = in.readInt()
        val ncol = in.readInt()
        logger.debug("... of dimensions: "+nrow+","+ncol)
        in.readInt() match {
          case INTEGER => (Array.fill(nrow) { Array.fill(ncol) { in.readInt() } },"Array[Array[Int]]")
          case DOUBLE => (Array.fill(nrow) { Array.fill(ncol) { in.readDouble() } },"Array[Array[Double]]")
          case BOOLEAN => (Array.fill(nrow) { Array.fill(ncol) { ( in.readInt() != 0 ) } },"Array[Array[Boolean]]")
          case STRING => (Array.fill(nrow) { Array.fill(ncol) { Helper.readString(in) } },"Array[Array[String]]")
          case _ => throw new RuntimeException("Protocol error")
        }
      case UNDEFINED_IDENTIFIER => throw new RuntimeException("Undefined identifier")
      case UNSUPPORTED_STRUCTURE => throw new RuntimeException("Unsupported data type")
      case _ => throw new RuntimeException("Protocol error")
    }
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Int`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getI0(identifier: String): Int = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int]
    case (a,"Double") => a.asInstanceOf[Double].toInt
    case (a,"Boolean") => if (a.asInstanceOf[Boolean]) 1 else 0
    case (a,"String") => a.asInstanceOf[String].toInt
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0)
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]](0).toInt
    case (a,"Array[Boolean]") => if ( a.asInstanceOf[Array[Boolean]](0) ) 1 else 0
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0).toInt
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Int")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to a `Double`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getD0(identifier: String): Double = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int].toDouble
    case (a,"Double") => a.asInstanceOf[Double]
    case (a,"Boolean") => if (a.asInstanceOf[Boolean]) 1.0 else 0.0
    case (a,"String") => a.asInstanceOf[String].toDouble
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0).toDouble
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]](0)
    case (a,"Array[Boolean]") => if ( a.asInstanceOf[Array[Boolean]](0) ) 1.0 else 0.0
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0).toDouble
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Double")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to a `Boolean`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getB0(identifier: String): Boolean = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int] != 0
    case (a,"Double") => a.asInstanceOf[Double] != 0.0
    case (a,"Boolean") => a.asInstanceOf[Boolean]
    case (a,"String") => a.asInstanceOf[String].toLowerCase != "false"
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0) != 0
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]](0) != 0.0
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]](0)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0).toLowerCase != "false"
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Boolean")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to a `string`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getS0(identifier: String): String = get(identifier) match {
    case (a,"Int") => a.asInstanceOf[Int].toString
    case (a,"Double") => a.asInstanceOf[Double].toString
    case (a,"Boolean") => a.asInstanceOf[Boolean].toString
    case (a,"String") => a.asInstanceOf[String]
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]](0).toString
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]](0).toString
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]](0).toString
    case (a,"Array[String]") => a.asInstanceOf[Array[String]](0)
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to String")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Int]`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getI1(identifier: String): Array[Int] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int])
    case (a,"Double") => Array(a.asInstanceOf[Double].toInt)
    case (a,"Boolean") => Array(if (a.asInstanceOf[Boolean]) 1 else 0)
    case (a,"String") => Array(a.asInstanceOf[String].toInt)
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]]
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]].map(_.toInt)
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]].map(x => if (x) 1 else 0)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]].map(_.toInt)
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Int]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Double]`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getD1(identifier: String): Array[Double] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int].toDouble)
    case (a,"Double") => Array(a.asInstanceOf[Double])
    case (a,"Boolean") => Array(if (a.asInstanceOf[Boolean]) 1.0 else 0.0)
    case (a,"String") => Array(a.asInstanceOf[String].toDouble)
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]].map(_.toDouble)
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]]
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]].map(x => if (x) 1.0 else 0.0)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]].map(_.toDouble)
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Double]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Boolean]`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getB1(identifier: String): Array[Boolean] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int] != 0)
    case (a,"Double") => Array(a.asInstanceOf[Double] != 0.0)
    case (a,"Boolean") => Array(a.asInstanceOf[Boolean])
    case (a,"String") => Array(a.asInstanceOf[String].toLowerCase != "false")
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]].map(_ != 0)
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]].map(_ != 0.0)
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]]
    case (a,"Array[String]") => a.asInstanceOf[Array[String]].map(_.toLowerCase != "false")
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Boolean]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[string]`.
  *
  * Integers, doubles, Booleans, and strings are supported.  Vectors (i.e. arrays) of these types are also supported by
  * converting the first element.  Matrices (i.e. rectangular arrays of arrays) are not supported.
  */
  def getS1(identifier: String): Array[String] = get(identifier) match {
    case (a,"Int") => Array(a.asInstanceOf[Int].toString)
    case (a,"Double") => Array(a.asInstanceOf[Double].toString)
    case (a,"Boolean") => Array(a.asInstanceOf[Boolean].toString)
    case (a,"String") => Array(a.asInstanceOf[String])
    case (a,"Array[Int]") => a.asInstanceOf[Array[Int]].map(_.toString)
    case (a,"Array[Double]") => a.asInstanceOf[Array[Double]].map(_.toString)
    case (a,"Array[Boolean]") => a.asInstanceOf[Array[Boolean]].map(_.toString)
    case (a,"Array[String]") => a.asInstanceOf[Array[String]]
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[String]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Array[Int]]`.
  *
  * Matrices (i.e. rectangular arrays of arrays) of integers, doubles, Booleans, and strings are supported.  Integers, doubles,
  * Booleans, and strings themselves are not supported.  Vectors (i.e. arrays) of these
  * types are also not supported.
  */
  def getI2(identifier: String): Array[Array[Int]] = get(identifier) match {
    case (a,"Array[Array[Int]]") => a.asInstanceOf[Array[Array[Int]]]
    case (a,"Array[Array[Double]]") => a.asInstanceOf[Array[Array[Double]]].map(_.map(_.toInt))
    case (a,"Array[Array[Boolean]]") => a.asInstanceOf[Array[Array[Boolean]]].map(_.map(x => if (x) 1 else 0))
    case (a,"Array[Array[String]]") => a.asInstanceOf[Array[Array[String]]].map(_.map(_.toInt))
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Array[Int]]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Array[Double]]`.
  *
  * Matrices (i.e. rectangular arrays of arrays) of integers, doubles, Booleans, and strings are supported.  Integers, doubles,
  * Booleans, and strings themselves are not supported.  Vectors (i.e. arrays) of these
  * types are also not supported.
  */
  def getD2(identifier: String): Array[Array[Double]] = get(identifier) match {
    case (a,"Array[Array[Int]]") => a.asInstanceOf[Array[Array[Int]]].map(_.map(_.toDouble))
    case (a,"Array[Array[Double]]") => a.asInstanceOf[Array[Array[Double]]]
    case (a,"Array[Array[Boolean]]") => a.asInstanceOf[Array[Array[Boolean]]].map(_.map(x => if (x) 1.0 else 0.0))
    case (a,"Array[Array[String]]") => a.asInstanceOf[Array[Array[String]]].map(_.map(_.toDouble))
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Array[Double]]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Array[Boolean]]`.
  *
  * Matrices (i.e. rectangular arrays of arrays) of integers, doubles, Booleans, and strings are supported.  Integers, doubles,
  * Booleans, and strings themselves are not supported.  Vectors (i.e. arrays) of these
  * types are also not supported.
  */
  def getB2(identifier: String): Array[Array[Boolean]] = get(identifier) match {
    case (a,"Array[Array[Int]]") => a.asInstanceOf[Array[Array[Int]]].map(_.map(_ != 0))
    case (a,"Array[Array[Double]]") => a.asInstanceOf[Array[Array[Double]]].map(_.map(_ != 0.0))
    case (a,"Array[Array[Boolean]]") => a.asInstanceOf[Array[Array[Boolean]]]
    case (a,"Array[Array[String]]") => a.asInstanceOf[Array[Array[String]]].map(_.map(_.toLowerCase != "false"))
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Array[Boolean]]")
  }

  /** Calls '''`get(identifier,false)`''' and converts the result to an `Array[Array[string]]`.
  *
  * Matrices (i.e. rectangular arrays of arrays) of integers, doubles, Booleans, and strings are supported.  Integers, doubles,
  * Booleans, and strings themselves are not supported.  Vectors (i.e. arrays) of these
  * types are also not supported.
  */
  def getS2(identifier: String): Array[Array[String]] = get(identifier) match {
    case (a,"Array[Array[Int]]") => a.asInstanceOf[Array[Array[Int]]].map(_.map(_.toString))
    case (a,"Array[Array[Double]]") => a.asInstanceOf[Array[Array[Double]]].map(_.map(_.toString))
    case (a,"Array[Array[Boolean]]") => a.asInstanceOf[Array[Array[Boolean]]].map(_.map(_.toString))
    case (a,"Array[Array[String]]") => a.asInstanceOf[Array[Array[String]]]
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to Array[Array[String]]")
  }

  /** Calls '''`get(identifier,true)`''' and converts the result to an `[[RObject]]`.
  *
  * The value is merely wrapped using [[RObjectRef]] and objects of any type are supported.
  */
  def getR(identifier: String): RObjectRef = get(identifier,true) match {
    case (a,"RObject") => a.asInstanceOf[RObjectRef]
    case (_,tp) => throw new RuntimeException(s"Unable to cast ${tp} to RObject")
  }

  /**
  * Reclaims memory associated with __'''all'''__ R references, including any instances of [[RObjectRef]] that are still in
  * memory.
  */
  def gc(): Unit = {
    logger.debug("Sending GC request.")
    out.writeInt(GC)
    out.flush()
  }

}

/** The companion object to the [[RClient]] class used to create an instance of the [[RClient]] class in a JVM-based
 * application.
*
* An object `R` is an [[RClient]] instance available in a Scala interpreter created by calling the function
* `scalaInterpreter` from the package [[http://cran.r-project.org/package=rscala rscala]].  It is through this instance
* `R` that callbacks to the original [[http://www.r-project.org R]] interpreter are possible.
* 
* The paths of the rscala's JARs, for both Scala 2.10 and 2.11, are available from [[http://www.r-project.org R]] using
* `rscala::rscalaJar()`.  To get just the JAR for Scala 2.11, for example, use `rscala::rscalaJar("2.11")`.
* 
* {{{ val R = RClient() }}}
*/
object RClient {

  import scala.sys.process._
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  val OS = sys.props("os.name").toLowerCase match {
    case s if s.startsWith("""windows""") => "windows"
    case s if s.startsWith("""linux""") => "linux"
    case s if s.startsWith("""unix""") => "linux"
    case s if s.startsWith("""mac""") => "macintosh"
    case _ => throw new RuntimeException("Unrecognized OS")
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
    if ( result == null ) throw new RuntimeException("Cannot locate R using Windows registry.")
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

  /**
  * Returns an instance of the [[RClient]] class by running the `R` executable in the path.
  */
  def apply(): RClient = apply(defaultRCmd)

  /** Returns an instance of the [[RClient]] class using the path specified by `rCmd` and specifying whether debugging
  * output should be display and the `timeout` to establish a connection with the R interpreter.
  */
  def apply(rCmd: String, libdir : String = "",debug: Boolean = false, timeout: Int = 60): RClient = {
    logger.info("Creating processIO")
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
    val sockets = new ScalaSockets(portsFile.getAbsolutePath)
    sockets.out.writeInt(OK)
    sockets.out.flush()
    try {
      assert( Helper.readString(sockets.in) == org.apache.zeppelin.rinterpreter.rscala.Version )
    } catch {
      case _: Throwable => throw new RuntimeException("The JAR file is not compatible with installed version of the rscala package.  Use the JAR indicated running `rscala::rscalaJar("+util.Properties.versionString.replaceFirst("""\.\d+$""","")+")` in R.")
    }
    apply(sockets.in,sockets.out)
  }

  /** __For rscala developers only__: Returns an instance of the [[RClient]] class.  */
  def apply(in: DataInputStream, out: DataOutputStream): RClient = new RClient(in,out)

}

