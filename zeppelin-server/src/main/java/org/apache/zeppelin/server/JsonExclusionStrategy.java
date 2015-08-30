package org.apache.zeppelin.server;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import org.apache.zeppelin.interpreter.InterpreterOption;

/**
 * Created by eranw on 8/30/15.
 * Omit InterpreterOption from serialization
 */
public class JsonExclusionStrategy implements ExclusionStrategy {

  public boolean shouldSkipClass(Class<?> arg0) {
    //exclude only InterpreterOption
    return InterpreterOption.class.equals(arg0);
  }

  public boolean shouldSkipField(FieldAttributes f) {

    return false;
  }
}
