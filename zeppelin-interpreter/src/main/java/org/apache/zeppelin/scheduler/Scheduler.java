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

package org.apache.zeppelin.scheduler;

import java.util.List;

/**
 * Interface for scheduler. Scheduler is used for manage the lifecycle of job.
 * Including query, submit and cancel job.
 *
 * Scheduler can run both in Zeppelin Server and Interpreter Process. e.g. RemoveScheduler run
 * in Zeppelin Server side while FIFOScheduler run in Interpreter Process.
 */
public interface Scheduler extends Runnable {

  String getName();

  List<Job> getAllJobs();

  Job getJob(String jobId);

  void submit(Job job);

  Job cancel(String jobId);

  void stop();

}
