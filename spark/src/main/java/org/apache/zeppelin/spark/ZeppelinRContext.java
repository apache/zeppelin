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
import org.apache.spark.sql.SQLContext;

/**
 * Contains the Spark and Zeppelin Contexts made available to SparkR.
 */
public class ZeppelinRContext {
  private static SparkContext sparkContext;
  private static SQLContext sqlContext;
  private static ZeppelinContext zeppelinContext;
  private static Object sparkSession;

  public static void setSparkContext(SparkContext sparkContext) {
    ZeppelinRContext.sparkContext = sparkContext;
  }

  public static void setZeppelinContext(ZeppelinContext zeppelinContext) {
    ZeppelinRContext.zeppelinContext = zeppelinContext;
  }

  public static void setSqlContext(SQLContext sqlContext) {
    ZeppelinRContext.sqlContext = sqlContext;
  }

  public static void setSparkSession(Object sparkSession) {
    ZeppelinRContext.sparkSession = sparkSession;
  }

  public static SparkContext getSparkContext() {
    return sparkContext;
  }

  public static SQLContext getSqlContext() {
    return sqlContext;
  }

  public static ZeppelinContext getZeppelinContext() {
    return zeppelinContext;
  }

  public static Object getSparkSession() {
    return sparkSession;
  }
}
