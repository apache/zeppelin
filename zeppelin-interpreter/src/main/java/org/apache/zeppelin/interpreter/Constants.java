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

package org.apache.zeppelin.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Interpreter related constants
 * 
 *
 */
public class Constants {
  public static final String ZEPPELIN_INTERPRETER_PORT = "zeppelin.interpreter.port";

  public static final String ZEPPELIN_INTERPRETER_HOST = "zeppelin.interpreter.host";

  public static final String EXISTING_PROCESS = "existing_process";

  public static final int ZEPPELIN_INTERPRETER_DEFAUlT_PORT = 29914;

  public static final int ZEPPELIN_INTERPRETER_OUTPUT_LIMIT = 1024 * 100;

  public static final Map<String, TimeUnit> TIME_SUFFIXES;

  static {
    TIME_SUFFIXES = new HashMap<>();
    TIME_SUFFIXES.put("us", TimeUnit.MICROSECONDS);
    TIME_SUFFIXES.put("ms", TimeUnit.MILLISECONDS);
    TIME_SUFFIXES.put("s", TimeUnit.SECONDS);
    TIME_SUFFIXES.put("m", TimeUnit.MINUTES);
    TIME_SUFFIXES.put("min", TimeUnit.MINUTES);
    TIME_SUFFIXES.put("h", TimeUnit.HOURS);
    TIME_SUFFIXES.put("d", TimeUnit.DAYS);
  }

}
