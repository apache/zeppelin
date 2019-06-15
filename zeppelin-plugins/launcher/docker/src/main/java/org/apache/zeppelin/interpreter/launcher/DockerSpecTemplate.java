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
package org.apache.zeppelin.interpreter.launcher;

import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class DockerSpecTemplate extends HashMap<String, Object> {
  public String render(File templateFile) throws IOException {
    String template = FileUtils.readFileToString(templateFile, Charset.defaultCharset());
    return render(template);
  }

  public String render(String template) {
    ClassLoader oldClazzLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      Jinjava jinja = new Jinjava();
      return jinja.render(template, this);
    } finally {
      Thread.currentThread().setContextClassLoader(oldClazzLoader);
    }
  }

  public void loadProperties(Properties properties) {
    Set<Entry<Object, Object>> entries = properties.entrySet();
    for (Entry entry : entries) {
      String key = (String) entry.getKey();
      Object value = entry.getValue();

      String[] keySplit = key.split("[.]");
      Map<String, Object> target = this;
      for (int i = 0; i < keySplit.length - 1; i++) {
        if (!target.containsKey(keySplit[i])) {
          HashMap subEntry = new HashMap();
          target.put(keySplit[i], subEntry);
          target = subEntry;
        } else {
          Object subEntry = target.get(keySplit[i]);
          if (!(subEntry instanceof Map)) {
            HashMap replace = new HashMap();
            replace.put("_", subEntry);
            target.put(keySplit[i], replace);
          }
          target = (Map<String, Object>) target.get(keySplit[i]);
        }
      }

      if (target.get(keySplit[keySplit.length - 1]) instanceof Map) {
        ((Map) target.get(keySplit[keySplit.length - 1])).put("_", value);
      } else {
        target.put(keySplit[keySplit.length - 1], value);
      }
    }
  }
}
