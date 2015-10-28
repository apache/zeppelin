package org.apache.zeppelin.interpreter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.util.Map.Entry;

import org.apache.zeppelin.interpreter.InterpreterResult.Code;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Separates results from everything else.
 * 
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class InterpreterResultSerializer
    implements JsonSerializer<InterpreterResult>, JsonDeserializer<InterpreterResult> {

  RemoteResultRepo repo;
  String id;

  public InterpreterResultSerializer() {
  }

  public InterpreterResultSerializer(RemoteResultRepo repo, String ParagraphId) {
    this.repo = repo;
    this.id = ParagraphId;
  }

  @Override
  public InterpreterResult deserialize(JsonElement json, Type typeOfT,
      JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject object = json.getAsJsonObject();

    String code = object.get("code").getAsString();
    String type = object.get("type").getAsString();
    InterpreterResult interpreterResult = new InterpreterResult(null);

    String result = null;
    try {
      for (Entry<String, JsonElement> entry : object.entrySet()) {
        try {
          Field f;
          f = InterpreterResult.class.getDeclaredField(entry.getKey());
          f.setAccessible(true);
          f.set(interpreterResult, context.deserialize(entry.getValue(), f.getType()));
        } catch (NoSuchFieldException ex) {
        }
      }
      // Only fetch from the repo if it's not stored already.
      if (interpreterResult.msg == null)
        result = repo.get(id);
    } catch (IOException | IllegalArgumentException | IllegalAccessException e) {
      throw new JsonParseException(e.getMessage());
    }
    return interpreterResult;
  }

  @Override
  public JsonElement serialize(InterpreterResult src, Type typeOfSrc,
      JsonSerializationContext context) {
    try {
      JsonObject object = new JsonObject();
      repo.save(src.message(), id);
      object.addProperty("code", src.code().toString());
      object.addProperty("type", src.type().toString());
      return object;
    } catch (IOException ex) {
      throw new JsonParseException(ex);
    }
  }
}
