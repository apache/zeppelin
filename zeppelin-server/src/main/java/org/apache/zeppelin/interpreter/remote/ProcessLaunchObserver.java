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

package org.apache.zeppelin.interpreter.remote;

/**
 * Observes the output produced while an interpreter process is launched.
 *
 * <p>Implementations can use the output to discover an external application and monitor its
 * lifecycle. This interface deliberately exposes no cluster-manager-specific types so that
 * implementations and their dependencies can live in optional plugins.
 */
@FunctionalInterface
public interface ProcessLaunchObserver {

  ProcessLaunchObserver NO_OP = (launchOutput, interpreterProcess) -> { };

  void onProcessLaunch(
      String launchOutput, RemoteInterpreterManagedProcess interpreterProcess);
}
