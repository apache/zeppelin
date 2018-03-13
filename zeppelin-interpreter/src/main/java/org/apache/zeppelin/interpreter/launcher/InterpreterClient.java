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

package org.apache.zeppelin.interpreter.launcher;

/**
 * Interface to InterpreterClient which is created by InterpreterLauncher. This is the component
 * that is used to for the communication from zeppelin-server process to zeppelin interpreter
 * process.
 */
public interface InterpreterClient {

  String getInterpreterSettingName();

  void start(String userName);

  void stop();

  String getHost();

  int getPort();

  boolean isRunning();
}
