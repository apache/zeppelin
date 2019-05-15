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
 * InterpreterFactory Interface
 * Provides the interface to the ClusterManagerServer
 * through the user, nodeId, replName query interpreter
 * Since the InterpreterFactory is in the zeppelin-zengine module,
 * the ClusterManagerServer in the zeppelin-interpreter module
 * cannot access InterpreterFactory#getInterpreter(...),
 * So access through the interface.
 */
public interface InterpreterFactoryInterface {
  Interpreter getInterpreter(String user,
                             String noteId,
                             String replName,
                             String defaultInterpreterSetting)
      throws InterpreterNotFoundException;
}
