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

package org.apache.zeppelin.livy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Livy SparkSessionMap for Zeppelin.
 */
public class LivySparkSessionMap {
  private static LivySparkSessionMap instance = null;
  protected static Map<String, Integer> userSparkSessionMap = 
        new ConcurrentHashMap<String, Integer>(); 
  private static Object mutex = new Object();

  
  protected LivySparkSessionMap() {
    // Exists only to defeat instantiation
  }
  public static LivySparkSessionMap getInstance() {
    if (instance == null) {
      synchronized (mutex){
        if (instance == null) instance = new LivySparkSessionMap();
      }
    }
    return instance;
  }
  public void setSparkUserSessionMap(String user, Integer sessionInt) {
    userSparkSessionMap.put(user, sessionInt);
  }
  public void deleteSparkUserSessionMap(String user, Integer sessionInt) {
    userSparkSessionMap.remove(user, sessionInt);
  }
  public void deleteSparkUserSessionMap(String user) {
    userSparkSessionMap.remove(user);
  }
  public Integer getSparkUserSession(String user) {
    return userSparkSessionMap.get(user); 
  }
  public Map<String, Integer> getSparkUserSessionMap() {
    return userSparkSessionMap; 
  }
}
