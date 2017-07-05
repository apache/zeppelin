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

package org.apache.zeppelin.cluster;

import java.util.Map;
import java.util.Properties;

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;

/**
 * This is an abstraction class for implementing cluster managing service.
 */
public abstract class ClusterManager {

  /**
   * Can throw `RuntimeException`
   */
  public abstract void start();

  /**
   * Can throw `RuntimeException`
   */
  public abstract void stop();

  public abstract RemoteInterpreterProcess createInterpreter(String id, String name,
      String groupName, Map<String, String> env, Properties properties, int connectTimeout,
      RemoteInterpreterProcessListener listener, ApplicationEventListener appListener,
      String homeDir, String interpreterDir)
      throws InterpreterException;

  public abstract void releaseResource(String id);
}
