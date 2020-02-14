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

package org.apache.zeppelin.spark.kotlin;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.kotlin.context.KotlinReceiver;

/**
 * Implicit receiver for Kotlin REPL with Spark's context (see KotlinReceiver for more details)
 */
public class SparkKotlinReceiver extends KotlinReceiver {
  public final Object _sparkObject;
  public final JavaSparkContext sc;
  public final SQLContext sqlContext;
  public final ZeppelinContext z;

  public SparkKotlinReceiver(Object spark,
                             JavaSparkContext sc,
                             SQLContext sqlContext,
                             ZeppelinContext z) {
    this._sparkObject = spark;
    this.sc = sc;
    this.sqlContext = sqlContext;
    this.z = z;
  }
}
