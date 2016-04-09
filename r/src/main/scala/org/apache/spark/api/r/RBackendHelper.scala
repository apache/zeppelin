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
With grattitude to Shivaram for advice regarding how to get SparkR talking to an existing SparkContext in Java
 */
package org.apache.spark.api.r

class RBackendHelper(val backend : RBackend) {



  def close() : Unit = backend.close()


  var port : Int = 0

  def init() : Int = {
    port = backend.init()
    port
  }

  val backendThread : Thread = new Thread("SparkR backend") {
    override def run() {
      backend.run()
    }
  }

  def start() : Thread = {
    if (port == 0) throw new RuntimeException("BackendHelper must be initialized before starting")
    if (!backendThread.isAlive) backendThread.start()
    backendThread
  }


/*
The sequence is:
1.  Before initializing spark in R, after loading library, Backend goes up and starts listening.  (Note that its able to execute arbitrary methods!!!  We can use it for
zeppelin context!!!)
2.  Tell SparkR to make a connection to the backend, setting the EXISTING port to the one in backendhelper.
3.  Track sparkR.init, but where it calls spark/R/pkg/R/sparkR.R calls org.apache.spark.api.r.RRDD.createSparkContext to get sc,
which is then returned as a jobj link, instead call RBackendHelper.getSC
  3a Actually the object returned right now is of type JavaSparkContext ?????  Need to understand this
4.  SparkR for the other contexts calls related methods, org.apache.spark.sql.api.r.SQLUtils.createSQLContext and
org.apache.spark.sql.hive.HiveContext is just made new, with the jobj reference assigned to an object.  We should track
the same pattern as above.


 */
}


object RBackendHelper {

/*
This function creates a new SparkContext, but does not register it, based on whatever properties are provided.
Its for testing purposes and should never be called
 */
//  def buildSparkContext( props : Properties) : SparkContext = {
//    val traversableProps : Traversable[(String, String)] = propertiesAsScalaMap(props)
//    val conf = new SparkConf().setAll(traversableProps)
//    conf.setIfMissing("spark.master", "local")
//  conf.setIfMissing("spark.app.name", "ZeppelinRContext")
//    conf.validateSettings()
//    new SparkContext(conf)
//  }

  def apply() : RBackendHelper = new RBackendHelper(new RBackend())

}