package org.apache.zeppelin.rinterpreter.rscala

/**
 * Created by aelberg on 8/1/15.
 */
abstract class RObject (objType : Int, // the rScala protocol code
               ref: String, // the rScala reference
               identifier : String = null, // the R identifier
               var objClass : Vector[String] = null // the R result of class()
                ) {

  private lazy val rClass : Vector[String] =  objClass match {
        case null => null // fix me!
        case x : Vector[String] => x
  }
  def getRef() : String = ref
  def getRClass : Vector[String] = rClass
  def getIdentifier : String = identifier
}
