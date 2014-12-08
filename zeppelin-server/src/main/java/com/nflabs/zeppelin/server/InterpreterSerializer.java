package com.nflabs.zeppelin.server;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.nflabs.zeppelin.interpreter.Interpreter;


/**
 * Interpreter class serializer for gson 
 *
 */
public class InterpreterSerializer implements JsonSerializer<Interpreter> {

  @Override
  public JsonElement serialize(Interpreter interpreter, Type type,
      JsonSerializationContext context) {
    JsonObject json = new JsonObject();
    json.addProperty("class", interpreter.getClassName());
    
    return json;
  }

}
