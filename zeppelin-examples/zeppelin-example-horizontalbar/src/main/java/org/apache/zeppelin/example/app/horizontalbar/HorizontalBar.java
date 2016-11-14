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
package org.apache.zeppelin.example.app.horizontalbar;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.helium.Application;
import org.apache.zeppelin.helium.ApplicationContext;
import org.apache.zeppelin.helium.ApplicationException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.dev.ZeppelinApplicationDevServer;
import org.apache.zeppelin.resource.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * Basic example application.
 * TableData for input
 */
public class HorizontalBar extends Application {
  private final Logger logger = LoggerFactory.getLogger(HorizontalBar.class);

  InterpreterResult result;

  public HorizontalBar(ApplicationContext context) {
    super(context);
  }

  @Override
  public void run(ResourceSet resources) throws ApplicationException, IOException {
    // Get data from resource args
    result = (InterpreterResult) resources.get(0).get();

    // create element
    println(String.format(
        "<div id=\"horizontalbar_%s\" style=\"height:400px\"><svg></svg></div>",
        context().getApplicationInstanceId()));
    // write js
    printResourceAsJavascript("example/app/horizontalbar/horizontalbar.js");
  }

  @Override
  public void unload() throws ApplicationException {
  }

  /**
   * Development mode
   */
  public static void main(String[] args) throws Exception {
    LocalResourcePool pool = new LocalResourcePool("dev");
    InputStream ins = ClassLoader.getSystemResourceAsStream(
        "example/app/horizontalbar/horizontalbar_mockdata.txt");
    InterpreterResult result = new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TABLE,
        IOUtils.toString(ins));
    pool.put(WellKnownResourceName.ZeppelinTableResult.name(), result);

    ZeppelinApplicationDevServer devServer = new ZeppelinApplicationDevServer(
        HorizontalBar.class.getName(),
        pool.getAll());

    devServer.start();
    devServer.join();
  }
}
