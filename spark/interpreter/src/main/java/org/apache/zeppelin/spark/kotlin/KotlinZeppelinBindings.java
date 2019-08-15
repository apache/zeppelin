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

public class KotlinZeppelinBindings {
  public static final String Z_SELECT_KOTLIN_SYNTAX =
      "import org.apache.zeppelin.display.ui.OptionInput.ParamOption;\n" +
      "import org.apache.zeppelin.spark.SparkZeppelinContext\n" +
      "import scala.collection.JavaConverters;\n" +
      "fun SparkZeppelinContext.select(" +
          "name: String, defaultValue: Any?, options: List<Pair<Any?, String>>): Any {\n" +
      "    val tupleList = options.map{scala.Tuple2(it.first, it.second)}\n" +
      "    val seq = JavaConverters" +
          ".asScalaIteratorConverter(tupleList.iterator())" +
          ".asScala().toSeq()\n" +
      "    return select(name, seq)\n" +
      "}\n" +
      "\n" +
      "fun SparkZeppelinContext.select(name: String, options: List<Pair<Any?, String>>): Any? {\n" +
      "    return select(name, \"\", options)\n" +
      "}";
}