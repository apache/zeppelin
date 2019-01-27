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

import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;

import java.util.Properties;

/**
 * Abstract class for SparkInterpreter. For the purpose of co-exist of NewSparkInterpreter
 * and OldSparkInterpreter
 */
public abstract class AbstractSparkInterpreter extends Interpreter {

  private SparkInterpreter parentSparkInterpreter;

  public AbstractSparkInterpreter(Properties properties) {
    super(properties);
  }

  public abstract SparkContext getSparkContext();

  public abstract SQLContext getSQLContext();

  public abstract Object getSparkSession();

  public abstract boolean isSparkContextInitialized();

  public abstract SparkVersion getSparkVersion();

  public abstract JavaSparkContext getJavaSparkContext();

  public abstract void populateSparkWebUrl(InterpreterContext ctx);

  public abstract SparkZeppelinContext getZeppelinContext();

  public abstract String getSparkUIUrl();

  public abstract boolean isUnsupportedSparkVersion();

  public void setParentSparkInterpreter(SparkInterpreter parentSparkInterpreter) {
    this.parentSparkInterpreter = parentSparkInterpreter;
  }

  public SparkInterpreter getParentSparkInterpreter() {
    return parentSparkInterpreter;
  }
}
