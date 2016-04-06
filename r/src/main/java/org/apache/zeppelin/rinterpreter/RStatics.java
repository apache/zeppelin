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

/*
The purpose of this class is to provide something for R to call through the backend
to bootstrap.
 */

package org.apache.zeppelin.rinterpreter;

import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.spark.ZeppelinContext;

/**
 * RStatics provides static class methods that can be accessed through the SparkR bridge
 *
 */
public class RStatics {
  private static SparkContext sc = null;
  private static ZeppelinContext z = null;
  private static SQLContext sql = null;
  private static RContext rCon = null;

  public static SparkContext setSC(SparkContext newSC) {
    sc = newSC;
    return sc;
  }

  public static ZeppelinContext setZ(ZeppelinContext newZ) {
    z = newZ;
    return z;
  }

  public static SQLContext setSQL(SQLContext newSQL) {
    sql = newSQL;
    return sql;
  }

  public static JavaSparkContext getJSC() {
    return new JavaSparkContext(sc);
  }

  public static SparkContext getSC() {
    return sc;
  }

  public static SQLContext getSQL() {
    return sql;
  }

  public static Object getZ(String name) {
    return z.get(name);
  }

  public static void putZ(String name, Object obj) {
    z.put(name, obj);
  }

  public static RContext getRCon() {
    return rCon;
  }
  public static RContext setrCon(RContext newrCon) {
    rCon = newrCon;
    return rCon;
  }
  public static Boolean testRDD(String name) {
    Object x = z.get(name);
    return (x instanceof org.apache.spark.api.java.JavaRDD);
  }
}
