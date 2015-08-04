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
package org.apache.zeppelin.spark.display

import org.apache.spark.SparkContext
import org.apache.zeppelin.context.ZeppelinContext
import org.apache.zeppelin.interpreter.InterpreterException
import org.slf4j.{LoggerFactory, Logger}

/**
 * Helper singleton to register
 * appropriate display function
 * depending on the Spark version
 */
object DisplayFunctionsHelper{

  val logger:Logger = LoggerFactory.getLogger(classOf[DisplayFunctionsHelper])

  def registerDisplayFunctions(sc: SparkContext, z: ZeppelinContext): Unit = {

    val dfClass = try {
      Option(Class.forName("org.apache.spark.sql.DataFrame"))
    } catch {
      case e: Throwable => None
    }

    val schemaRDDClass = try {
      Option(Class.forName("org.apache.spark.sql.SchemaRDD"))
    } catch {
      case e: Throwable => None
    }

    dfClass match {
      case Some(_) =>
        logger.info("Registering DisplayDataFrame function")
        z.registerDisplayFunction(new DisplayDataFrame(sc))
      case None => schemaRDDClass match {
        case Some(_) =>
          logger.info("Registering DisplaySchemaRDD function")
          z.registerDisplayFunction(new DisplaySchemaRDD(sc))
        case None => throw new InterpreterException("Can not road DataFrame/SchemaRDD class")
      }
    }

    logger.info("Registering DisplayRDD function")
    z.registerDisplayFunction(new DisplayRDD)

    logger.info("Registering DisplayTraversable function")
    z.registerDisplayFunction(new DisplayTraversable)

  }
}

class DisplayFunctionsHelper
