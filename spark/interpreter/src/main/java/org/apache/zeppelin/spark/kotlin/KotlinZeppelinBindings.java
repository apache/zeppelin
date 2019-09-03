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

/**
 * Pre-executed code on KotlinSparkInterpreter opening.
 */
public class KotlinZeppelinBindings {

  /**
   * Simpler Kotlin syntax for z.select
   */
  public static final String Z_SELECT_KOTLIN_SYNTAX =
      "import org.apache.zeppelin.display.ui.OptionInput.ParamOption\n" +
      "import org.apache.zeppelin.interpreter.BaseZeppelinContext\n" +
      "\n" +
      "fun BaseZeppelinContext.select(name: String, defaultValue: Any?, " +
          "options: List<Pair<Any?, String>>): Any? {\n" +
      "    return select(name, defaultValue, " +
          "options.map{ ParamOption(it.first, it.second) }.toTypedArray())\n" +
      "}\n" +
      "\n" +
      "fun BaseZeppelinContext.select(name: String, options: List<Pair<Any?, String>>): Any? {\n" +
      "    return select(name, \"\", options)\n" +
      "}";

  /**
   * Automatic imports for Spark SQL UDFs.
   */
  public static final String SPARK_UDF_IMPORTS =
      "import org.apache.spark.sql.types.DataTypes\n" +
      "import org.apache.spark.sql.functions.*\n" +
      "import org.apache.spark.sql.expressions.UserDefinedFunction\n" +
      "import org.apache.spark.sql.api.java.*";
}
