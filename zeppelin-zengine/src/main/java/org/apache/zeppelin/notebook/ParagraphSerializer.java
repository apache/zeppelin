package org.apache.zeppelin.notebook;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.reflect.FieldUtils;
import javax.naming.spi.DirStateFactory.Result;
import org.apache.zeppelin.interpreter.FilesystemResultRepo;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultSerializer;
import org.apache.zeppelin.interpreter.ResultRepoFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Serializes paragraphs.
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class ParagraphSerializer implements JsonSerializer<Paragraph>, JsonDeserializer<Paragraph> {

  private ResultRepoFactory factory;
  
  public ParagraphSerializer(ResultRepoFactory factory) {
    this.factory = factory; 
  }
  
  private static String getResultRepo(JsonElement json)
  {
    JsonElement repo = json.getAsJsonObject().get("config").getAsJsonObject().get("RESULT_REPO");
    if (repo == null)
      return null;
    return repo.getAsString();
  }
  
  @Override
  public Paragraph deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      Paragraph p = new Paragraph(null, null, null);
     // p = context.deserialize(json, typeOfT);
      Field[] fields = Paragraph.class.getFields();
      String resultRepo = getResultRepo(json);
      JsonObject object = json.getAsJsonObject();
      GsonBuilder builder = new GsonBuilder();
      String id = object.get("id").getAsString();
      
      builder.setPrettyPrinting();
      if (resultRepo != null && factory != null) {
        builder.registerTypeAdapter(InterpreterResult.class,
          new InterpreterResultSerializer(factory.getRepoByClassName(resultRepo), id));
      }
      Gson gson = builder.create();
      for (Entry<String, JsonElement> entry: object.entrySet()) {
        try {
          Field f;
          f = Paragraph.class.getDeclaredField(entry.getKey());
          f.setAccessible(true);
          f.set(p, gson.fromJson(entry.getValue(), f.getType()));
        } catch (NoSuchFieldException e) {
              // If the paragraph format has changed, we do not want to do anything
              // with the fields that are extraneous.
        }

      }
      return p;
    } catch (SecurityException | IllegalArgumentException | IllegalAccessException | IOException ex)
    { throw new JsonParseException(ex); }
    
  }


  @Override
  public JsonElement serialize(Paragraph src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      Field[] fields = FieldUtils.getAllFields(Paragraph.class);
      GsonBuilder builder = new GsonBuilder();
      JsonObject object = new JsonObject();
      Boolean serializeResult = src.getConfig().containsKey("RESULT_REPO") && factory != null;
      if (serializeResult) {
        builder.registerTypeAdapter(InterpreterResult.class,
          new InterpreterResultSerializer(
              factory.getRepoByClassName(src.getConfig().get("RESULT_REPO").toString()),
              src.getId()));
      }
      
      Gson gson = builder.create();
       
      for (Field f: fields)
      {
        if ((f.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.VOLATILE)) == 0 ) 
        {
          f.setAccessible(true);
          object.add(f.getName(), gson.toJsonTree(f.get(src), f.getType()));
        }
      }
      
      return object;
    } catch 
    (SecurityException | IllegalArgumentException | IllegalAccessException | IOException ex )
    { throw new JsonParseException(ex); }
  }
  
}
