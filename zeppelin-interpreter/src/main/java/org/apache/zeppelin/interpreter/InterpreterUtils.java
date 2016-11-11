/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.interpreter;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreter utility functions
 */
public class InterpreterUtils {

  public static String getMostRelevantMessage(Exception ex) {
    if (ex instanceof InvocationTargetException) {
      Throwable cause = ((InvocationTargetException) ex).getCause();
      if (cause != null) {
        return cause.getMessage();
      }
    }
    return ex.getMessage();
  }

  /**
   * Substitute variable in the Interpreter properties with values in the config
   * If the property value is ${name,defaultValue}, 
   * then the property value will be set to config.get("name").
   * If config.get("name") does not exist, then the property value will be set to defaultValue.
   * If the property value does not start with ${, then property value will be unchanged.
   * @param properties
   * @param config
   * @return
   */
  public static Properties substitute(Properties properties, Map<String, Object> config) {
    if (properties == null) {
      return properties;
    }
    Properties result = new Properties();
    Iterator<Entry<Object, Object>> it = properties.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Object, Object> pair = it.next();
      String mydata = pair.getValue().toString();
      Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
      Matcher matcher = pattern.matcher(mydata);
      if (matcher.find()) {
        String varString = matcher.group(1);
        String[] vars = varString.split(",", 2);
        String varName = vars[0];
        if (config != null && config.containsKey(varName)) {
          result.put(pair.getKey(), config.get(varName));
        } else if (vars.length > 1) {
          result.put(pair.getKey(), vars[1]);
        } else {
          result.put(pair.getKey(), pair.getValue());
        }
      } else {
        result.put(pair.getKey(), pair.getValue());
      }
    }
    return result;
  }
}
