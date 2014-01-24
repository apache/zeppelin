package com.nflabs.zeppelin.server;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.nflabs.zeppelin.zengine.api.Z;

public class ZAdapter implements JsonSerializer<Z>, JsonDeserializer<Z>{

	@Override
	public JsonElement serialize(Z src, Type typeOfSrc, JsonSerializationContext context) throws JsonParseException {
		JsonObject result = new JsonObject();
		result.add("type", new JsonPrimitive(src.getClass().getName()));
        result.add("properties", context.serialize(src, src.getClass()));
		return result;
	}

	@Override
	public Z deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject jsonObject = json.getAsJsonObject();
        String type = jsonObject.get("type").getAsString();
        JsonElement element = jsonObject.get("properties");
 
        try {
            return context.deserialize(element, Class.forName(type));
        } catch (ClassNotFoundException cnfe) {
            throw new JsonParseException("Unknown element type: " + type, cnfe);
        }
	}

}
