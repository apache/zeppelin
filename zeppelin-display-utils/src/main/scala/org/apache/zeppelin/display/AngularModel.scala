package org.apache.zeppelin.display

import org.apache.zeppelin.interpreter.InterpreterContext

/**
  * Represents ng-model
  */
class AngularModel(name: String) {
  val registry = InterpreterContext.get.getAngularObjectRegistry
  val noteId = InterpreterContext.get.getNoteId

  def this(name: String, newValue: Any) = {
    this(name)

    value(newValue)
  }


  /**
    * Get value of the model
    * @return
    */
  def apply(): Any = {
    value()
  }

  /**
    * Get value of the model
    * @return
    */
  def value(): Any = {
    val angularObject = registry.get(name, noteId)
    if (angularObject == null) {
      None
    } else {
      angularObject.get
    }
  }


  def apply(newValue: Any): Unit = {
    value(newValue)
  }

  def :=(newValue: Any) = {
    new AngularModel(name, newValue)
  }

  /**
    * Set value of the model
    * @param newValue
    */
  def value(newValue: Any): Unit = {
    var angularObject = registry.get(name, noteId)
    if (angularObject == null) {
      // create new object
      angularObject = registry.add(name, newValue, noteId)
    } else {
      angularObject.asInstanceOf[AngularObject[Any]].set(newValue)
    }
    angularObject.get()
  }

  def remove(): Any = {
    val angularObject = registry.get(name, noteId)
    registry.remove(name, noteId)

    if (angularObject == null) {
      None
    } else {
      angularObject.get
    }
  }
}


object AngularModel {
  def apply(name: String): AngularModel = {
    new AngularModel(name)
  }

  def apply(name: String, newValue: Any): AngularModel = {
    new AngularModel(name, newValue)
  }

}