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

package org.apache.zeppelin.spark;

import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.r.IRInterpreter;
import org.apache.zeppelin.r.ShinyInterpreter;

import java.util.Properties;

/**
 * The same function as ShinyInterpreter, but support Spark as well.
 */
public class SparkShinyInterpreter extends ShinyInterpreter {
  public SparkShinyInterpreter(Properties properties) {
    super(properties);
  }

  protected IRInterpreter createIRInterpreter() {
    SparkIRInterpreter interpreter = new SparkIRInterpreter(properties);
    try {
      interpreter.setSparkInterpreter(getInterpreterInTheSameSessionByClassName(SparkInterpreter.class));
      return interpreter;
    } catch (InterpreterException e) {
      throw new RuntimeException("Fail to set spark interpreter for SparkIRInterpreter", e);
    }
  }
}
