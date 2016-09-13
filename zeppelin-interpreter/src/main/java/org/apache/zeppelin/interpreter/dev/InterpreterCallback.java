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
 
package org.apache.zeppelin.interpreter;

/**
 * The InterpreterCallback interface makes it possible to bind the execution of
 * code into the interpreter repl conditionally to events.
 */
public interface InterpreterCallback {
  //Binds the callback cmd to the given event
  public void registerCallback(String event, String cmd);
   
  // Unbinds the callback cmd from the given event
  public void unregisterCallback(String event);
   
  // Retrieves the callback code (cmd) for the given event
  public String getCallback(String event);
}
