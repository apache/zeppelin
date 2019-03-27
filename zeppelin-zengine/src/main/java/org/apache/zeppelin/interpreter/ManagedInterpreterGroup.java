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

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * ManagedInterpreterGroup runs under zeppelin server
 */
public class ManagedInterpreterGroup extends InterpreterGroup {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagedInterpreterGroup.class);

  private InterpreterSetting interpreterSetting;
  private RemoteInterpreterProcess remoteInterpreterProcess; // attached remote interpreter process

  /**
   * Create InterpreterGroup with given id and interpreterSetting, used in ZeppelinServer
   * @param id
   * @param interpreterSetting
   */
  ManagedInterpreterGroup(String id, InterpreterSetting interpreterSetting) {
    super(id);
    this.interpreterSetting = interpreterSetting;
  }

  public InterpreterSetting getInterpreterSetting() {
    return interpreterSetting;
  }

  public synchronized RemoteInterpreterProcess getOrCreateInterpreterProcess(String userName,
                                                                             Properties properties)
      throws IOException {
    if (remoteInterpreterProcess == null) {
      LOGGER.info("Create InterpreterProcess for InterpreterGroup: " + getId());
      remoteInterpreterProcess = interpreterSetting.createInterpreterProcess(id, userName,
          properties);
      // RemoteInterpreter.java::resurrectionInvalidIntpProcess()
      // Detecting an invalid interpreter process Clean up session, 
      // Will clean up the ManagedInterpreterGroup
      // So may need to rebuild the ManagedInterpreterGroup
      ManagedInterpreterGroup managedIntpGroup = interpreterSetting.getInterpreterGroup(id);
      if (null != managedIntpGroup) {
        managedIntpGroup.setInterpreterProcess(remoteInterpreterProcess);
      }
      remoteInterpreterProcess.start(userName);
      interpreterSetting.getLifecycleManager().onInterpreterProcessStarted(this);
      getInterpreterSetting().getRecoveryStorage()
          .onInterpreterClientStart(remoteInterpreterProcess);
    }
    return remoteInterpreterProcess;
  }

  private void setInterpreterProcess(RemoteInterpreterProcess remoteIntpProcess) {
    remoteInterpreterProcess = remoteIntpProcess;
  }

  // Check that the remote interpreter process Whether it Service exception,
  // Causes it to be unusable.
  private boolean remoteIntpProcessIsInvalid() {
    if (null != remoteInterpreterProcess && !remoteInterpreterProcess.isRunning()) {
      // The remote interpreter process has been created, but it has stopped running.
      return true;
    }

    return false;
  }

  public RemoteInterpreterProcess getInterpreterProcess() {
    return remoteInterpreterProcess;
  }

  public RemoteInterpreterProcess getRemoteInterpreterProcess() {
    return remoteInterpreterProcess;
  }

  /**
   * Close all interpreter instances in this group
   */
  public void close() {
    LOGGER.info("Close InterpreterGroup: " + id);
    for (String sessionId : sessions.keySet()) {
      close(sessionId);
    }
  }

  /**
   * Close all interpreter instances in this session
   * @param sessionId
   */
  public synchronized void close(String sessionId) {
    LOGGER.info("Close Session: " + sessionId + " for interpreter setting: " +
            interpreterSetting.getName());
    closeSessionThread(sessions.remove(sessionId));
    //TODO(zjffdu) whether close InterpreterGroup if there's no session left in Zeppelin Server
    if (sessions.isEmpty() && interpreterSetting != null) {
      LOGGER.info("Remove this InterpreterGroup: {} as all the sessions are closed", id);
      interpreterSetting.removeInterpreterGroup(id);
      if (remoteInterpreterProcess != null) {
        LOGGER.info("Kill RemoteInterpreterProcess");
        remoteInterpreterProcess.stop();
        try {
          interpreterSetting.getRecoveryStorage().onInterpreterClientStop(remoteInterpreterProcess);
        } catch (IOException e) {
          LOGGER.error("Fail to store recovery data", e);
        }
        remoteInterpreterProcess = null;
      }
    }
  }

  private void closeSessionThread(Collection<Interpreter> interpreters) {
    if (interpreters == null) {
      return;
    }
    List<Thread> closeThreads = interpreters.stream()
            .map(interpreter -> new Thread(() ->
                    closeInterpreter(interpreter),
                    interpreter.getClass().getSimpleName() + "-closeSessionThread"))
            .peek(t -> t.setUncaughtExceptionHandler((th, e) ->
                    LOGGER.error("Interpreter close error", e)))
            .peek(Thread::start)
            .collect(Collectors.toList());

    for (Thread t : closeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        LOGGER.error("Can't wait interpreter closeSessionThread threads", e);
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void closeInterpreter(Interpreter interpreter) {
    Scheduler scheduler = interpreter.getScheduler();

    // Need to abort the task being executed
    // when actively shutting down the remote interpreter
    if (false == remoteIntpProcessIsInvalid()) {
      // ZEPPELIN-4031
      // But when zeppelin detects that the interpreter process is abnormal,
      // Can't abort the this task,
      // Because this will be because of the call to the job.abort() function.
      // Call the getOrCreateInterpreterProcess() function again,
      // Will wait for the connection to interpret the 30 second timeout setting.
      for (final Job job : scheduler.getAllJobs()) {
        job.abort();
        job.setStatus(Job.Status.ABORT);
        LOGGER.info("Job " + job.getJobName() + " aborted ");
      }

      try {
        LOGGER.info("Trying to close interpreter " + interpreter.getClassName());
        interpreter.close();
      } catch (InterpreterException e) {
        LOGGER.warn("Fail to close interpreter " + interpreter.getClassName(), e);
      }
    }

    //TODO(zjffdu) move the close of schedule to Interpreter
    SchedulerFactory.singleton().removeScheduler(scheduler.getName());
  }

  public synchronized List<Interpreter> getOrCreateSession(String user, String sessionId) {
    if (sessions.containsKey(sessionId)) {
      return sessions.get(sessionId);
    } else {
      List<Interpreter> interpreters = interpreterSetting.createInterpreters(user, id, sessionId);
      for (Interpreter interpreter : interpreters) {
        interpreter.setInterpreterGroup(this);
      }
      LOGGER.info("Create Session: {} in InterpreterGroup: {} for user: {}", sessionId, id, user);
      sessions.put(sessionId, interpreters);
      return interpreters;
    }
  }

}
