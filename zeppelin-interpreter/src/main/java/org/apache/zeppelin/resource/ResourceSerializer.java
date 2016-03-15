package org.apache.zeppelin.resource;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
/**
 * Serializes and Deserializes resources if they are serializable.
 */
public class ResourceSerializer implements JsonDeserializer<Resource>, JsonSerializer<Resource> {

  public ResourceSerializer() {
  }

  @Override
  public JsonElement serialize(Resource src, Type typeOfSrc, JsonSerializationContext context) {
    // This is straightforward at the moment.
    return context.serialize(src);
  }

  @Override
  public Resource deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    // This requires that we use the class that's stored in the element to deserialize.
    JsonObject obj =  json.getAsJsonObject();
    String className = obj.getAsJsonPrimitive("className").getAsString();

    Gson gson = new Gson();
    Object r;
    try {
      r = gson.fromJson(obj.get("r"), Class.forName(className));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Unable to deserialize the resource");
    }
    ResourceId id = gson.fromJson(obj.get("resourceId"), ResourceId.class);

    return new Resource(id, r);
  }

}