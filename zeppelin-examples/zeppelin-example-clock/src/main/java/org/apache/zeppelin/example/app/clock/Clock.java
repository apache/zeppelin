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
package org.apache.zeppelin.example.app.clock;

import org.apache.zeppelin.helium.Application;
import org.apache.zeppelin.helium.ApplicationContext;
import org.apache.zeppelin.helium.ApplicationException;
import org.apache.zeppelin.interpreter.dev.ZeppelinApplicationDevServer;
import org.apache.zeppelin.resource.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Basic example application.
 * Get java.util.Date from resource pool and display it
 */
public class Clock extends Application {
  private final Logger logger = LoggerFactory.getLogger(Clock.class);

  Date date;
  boolean shutdown = false;
  private Thread updateThread;

  public Clock(ApplicationContext context) {
    super(context);
  }

  @Override
  public void run(ResourceSet resources) throws ApplicationException {
    // Get data from resource args
    date = (Date) resources.get(0).get();

    // print view template
    try {
      context().out.writeResource("example/app/clock/clock.html");
    } catch (IOException e) {
      throw new ApplicationException(e);
    }

    if (updateThread == null) {
      start();
    }
  }


  public void start() {
    updateThread = new Thread() {
      public void run() {
        while (!shutdown) {
          // format date
          SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

          // put formatted string to angular object.
          context().getAngularObjectRegistry().add("date", df.format(date));

          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            // nothing todo
          }
          date = new Date(date.getTime() + 1000);
        }
      }
    };

    updateThread.start();
  }


  @Override
  public void unload() throws ApplicationException {
    shutdown = true;
    try {
      updateThread.join();
    } catch (InterruptedException e) {
      // nothing to do
    }
    context().getAngularObjectRegistry().remove("date");
  }

  /**
   * Development mode
   */
  public static void main(String[] args) throws Exception {
    LocalResourcePool pool = new LocalResourcePool("dev");
    pool.put("date", new Date());

    ZeppelinApplicationDevServer devServer = new ZeppelinApplicationDevServer(
        Clock.class.getName(),
        pool.getAll());

    devServer.start();
    devServer.join();
  }
}
