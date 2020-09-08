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


package org.apache.zeppelin.client;

/**
 * Job status.
 *
 * UNKNOWN - Job is not found in remote
 * READY - Job is not running, ready to run.
 * PENDING - Job is submitted to scheduler. but not running yet
 * RUNNING - Job is running.
 * FINISHED - Job finished run. with success
 * ERROR - Job finished run. with error
 * ABORT - Job finished by abort
 */
public enum Status {
  UNKNOWN, READY, PENDING, RUNNING, FINISHED, ERROR, ABORT;

  public boolean isReady() {
    return this == READY;
  }

  public boolean isRunning() {
    return this == RUNNING;
  }

  public boolean isPending() {
    return this == PENDING;
  }

  public boolean isCompleted() {
    return this == FINISHED || this == ERROR || this == ABORT;
  }
}
