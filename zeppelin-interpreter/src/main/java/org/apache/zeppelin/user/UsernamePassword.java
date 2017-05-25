/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.user;

import java.lang.reflect.Type;

import org.apache.zeppelin.util.StringXORer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Username and Password POJO
 */
public class UsernamePassword {

  private String username;
  private String password;

  public UsernamePassword(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public String toString() {
    return "UsernamePassword{" +
        "username='" + username + '\'' +
        '}';
  }

  /**
   * JSON serializer
   */
  public static class UsernamePasswordSerializer implements JsonSerializer<UsernamePassword> {

    @Override
    public JsonElement serialize(UsernamePassword src, Type typeOfSrc,
        JsonSerializationContext context) {
      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("username", src.getUsername());
      jsonObject.addProperty("password", StringXORer.encode(src.getPassword(), src.getUsername()));
      return jsonObject;
    }
  }

  /**
   * JSON deserializer
   */
  public static class UsernamePasswordDeserializer implements JsonDeserializer<UsernamePassword> {

    @Override
    public UsernamePassword deserialize(JsonElement json, Type typeOfT,
        JsonDeserializationContext context) throws JsonParseException {
      JsonObject jsonObject = (JsonObject) json;
      String username = null;
      String password = null;
      JsonElement usernameElement = jsonObject.get("username");
      if (usernameElement != null) {
        username = usernameElement.getAsString();
        JsonElement passwordElement = jsonObject.get("password");
        if (passwordElement != null) {
          password = StringXORer.decode(passwordElement.getAsString(), username);
        }
      }
      if (username != null && password != null) {
        return new UsernamePassword(username, password);
      }

      return null;
    }
  }
}
