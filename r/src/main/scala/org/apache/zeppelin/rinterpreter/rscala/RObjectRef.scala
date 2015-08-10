package org.apache.zeppelin.rinterpreter.rscala

/** A class used to wrap an arbitrary R object.
*
* {{{
* val R = RClient()
* R.a = Array(1,2,3,4,5)
* val ref = R.evalR("as.list(a)")
* R.evalD0(s"sum(unlist(\${ref}))")
* }}}
*/

import _root_.org.apache.zeppelin.rinterpreter.RContext
import org.apache.zeppelin.rinterpreter.RContext
case class RObjectRef(reference : String, id : String = null) extends RObject(Protocol.REFERENCE, reference, id) {

  override def toString() = ".$"+getRef()
  def instantiate(R : RContext) : RObject = ???

}

