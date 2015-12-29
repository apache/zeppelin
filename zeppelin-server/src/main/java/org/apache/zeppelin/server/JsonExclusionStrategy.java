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

package org.apache.zeppelin.server;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import org.apache.zeppelin.interpreter.InterpreterOption;

/**
 * Created by eranw on 8/30/15.
 * Omit InterpreterOption from serialization
 */
public class JsonExclusionStrategy implements ExclusionStrategy {

  public boolean shouldSkipClass(Class<?> arg0) {
    //exclude only InterpreterOption
    return InterpreterOption.class.equals(arg0);
  }

  public boolean shouldSkipField(FieldAttributes f) {

    return false;
  }
}
