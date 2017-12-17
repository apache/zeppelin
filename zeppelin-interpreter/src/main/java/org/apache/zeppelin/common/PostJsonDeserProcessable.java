/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.common;

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Interface for class that require post processing after deserialization from json.
 */
public interface PostJsonDeserProcessable {

  void postJsonDeser();

  public static final TypeAdapterFactory TypeAdapterFactory = new TypeAdapterFactory() {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

      return new TypeAdapter<T>() {
        @Override
        public void write(JsonWriter out, T value) throws IOException {
          delegate.write(out, value);
        }

        @Override
        public T read(JsonReader in) throws IOException {
          T obj = delegate.read(in);
          if (obj instanceof PostJsonDeserProcessable) {
            ((PostJsonDeserProcessable) obj).postJsonDeser();
          }
          return obj;
        }
      };
    }

  };

}
