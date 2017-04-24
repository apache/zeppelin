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
package org.apache.zeppelin.helium;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.resource.ResourceSet;

/**
 * Mock application
 */
public class MockApplication1 extends Application {
  boolean unloaded;
  int run;

  public MockApplication1(ApplicationContext context) {
    super(context);
    unloaded = false;
    run = 0;
  }

  @Override
  public void run(ResourceSet args) {
    run++;
  }

  @Override
  public void unload() {
    unloaded = true;
  }

  public boolean isUnloaded() {
    return unloaded;
  }

  public int getNumRun() {
    return run;
  }
}
