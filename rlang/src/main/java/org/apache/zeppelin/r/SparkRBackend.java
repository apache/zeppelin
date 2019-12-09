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
package org.apache.zeppelin.r;

import org.apache.spark.api.r.RBackend;
import scala.Tuple2;


/**
 * SparkRBackend is responsible for communication between r process and jvm process.
 * It uses Spark's RBackend to start a SocketServer in JVM side to listen request from R process.
 */
public class SparkRBackend {
  private static SparkRBackend singleton;

  private RBackend backend = new RBackend();
  private boolean started = false;
  private int portNumber = 0;
  private String secret = "";
  private Thread backendThread;

  public synchronized static SparkRBackend get() {
    if (singleton == null) {
      singleton = new SparkRBackend();
    }
    return singleton;
  }

  private SparkRBackend() {
    this.backendThread  = new Thread("SparkRBackend") {
      @Override
      public void run() {
        backend.run();
      }
    };
  }

  public void init(boolean isSecretSocketSupported) throws Exception {
    Class rBackendClass = RBackend.class;
    if (isSecretSocketSupported) {
      Tuple2<Integer, Object> result =
              (Tuple2<Integer, Object>) rBackendClass.getMethod("init").invoke(backend);
      portNumber = result._1;
      Object rAuthHelper = result._2;
      secret = (String) rAuthHelper.getClass().getMethod("secret").invoke(rAuthHelper);
    } else {
      portNumber = (Integer) rBackendClass.getMethod("init").invoke(backend);
    }
  }

  public void start() {
    backendThread.start();
    started = true;
  }

  public void close(){
    backend.close();
    try {
      backendThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public boolean isStarted() {
    return started;
  }

  public int port(){
    return portNumber;
  }

  public String socketSecret() {
    return secret;
  }
}
