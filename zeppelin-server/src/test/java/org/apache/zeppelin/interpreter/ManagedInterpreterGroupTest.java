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

import org.junit.jupiter.api.BeforeEach;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.eclipse.aether.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


class ManagedInterpreterGroupTest {

  private InterpreterSetting interpreterSetting;
  private ZeppelinConfiguration zConf;

  @BeforeEach
  public void setUp() throws IOException, RepositoryException {
    zConf = ZeppelinConfiguration.load();
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setConf(zConf)
        .create();
  }

  @Test
  void testInterpreterGroup() {
    ManagedInterpreterGroup interpreterGroup =
        new ManagedInterpreterGroup("group_1", interpreterSetting, zConf);
    assertEquals(0, interpreterGroup.getSessionNum());

    // create session_1
    List<Interpreter> interpreters = interpreterGroup.getOrCreateSession("user1", "session_1");
    assertEquals(3, interpreters.size());
    assertEquals(EchoInterpreter.class.getName(), interpreters.get(0).getClassName());
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreters.get(1).getClassName());
    assertEquals(1, interpreterGroup.getSessionNum());

    // get the same interpreters when interpreterGroup.getOrCreateSession is invoked again
    assertEquals(interpreters, interpreterGroup.getOrCreateSession("user1", "session_1"));
    assertEquals(1, interpreterGroup.getSessionNum());

    // create session_2
    List<Interpreter> interpreters2 = interpreterGroup.getOrCreateSession("user1", "session_2");
    assertEquals(3, interpreters2.size());
    assertEquals(EchoInterpreter.class.getName(), interpreters2.get(0).getClassName());
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreters2.get(1).getClassName());
    assertEquals(2, interpreterGroup.getSessionNum());

    // close session_1
    interpreterGroup.close("session_1");
    assertEquals(1, interpreterGroup.getSessionNum());

    // close InterpreterGroup
    interpreterGroup.close();
    assertEquals(0, interpreterGroup.getSessionNum());
  }

  @Test
  @Timeout(30)
  void close_doesNotDeadlockWithConcurrentOpen() throws Exception {
    ManagedInterpreterGroup group =
        new ManagedInterpreterGroup("g1", interpreterSetting, zConf);

    LockProbeInterpreter probe = new LockProbeInterpreter(new Properties());
    probe.setInterpreterGroup(group);
    List<Interpreter> s1 = new ArrayList<>();
    s1.add(probe);
    group.sessions.put("s1", s1);

    CountDownLatch openerHasIntp = new CountDownLatch(1);

    // "opener": mirrors open() lock ordering (interpreter -> group).
    Thread opener = new Thread(() -> {
      synchronized (probe) {                        // interpreter monitor
        openerHasIntp.countDown();
        try {
          // wait until the close worker is blocked trying to take the interpreter monitor, which
          // means the closer is holding the group monitor inside close(String).
          probe.closeReached.await();
          Thread w;
          while ((w = probe.worker) == null || w.getState() != Thread.State.BLOCKED) {
            Thread.onSpinWait();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        group.getOrCreateSession("u", "s2");        // needs the group monitor
      }
    }, "deadlock-opener");
    opener.setDaemon(true);

    // "closer": the real method under test.
    Thread closer = new Thread(() -> group.close("s1"), "deadlock-closer");
    closer.setDaemon(true);

    opener.start();
    assertTrue(openerHasIntp.await(5, TimeUnit.SECONDS),
        "opener failed to acquire the interpreter monitor");
    closer.start();

    opener.join(TimeUnit.SECONDS.toMillis(10));
    closer.join(TimeUnit.SECONDS.toMillis(10));

    if (opener.isAlive() || closer.isAlive()) {
      long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
      fail("Deadlock: ManagedInterpreterGroup.close() holds the group monitor while joining the "
          + "close-worker thread, which needs the interpreter monitor held by the concurrent "
          + "open(). opener.alive=" + opener.isAlive() + ", closer.alive=" + closer.isAlive()
          + ", jvmDetectedMonitorDeadlock=" + (deadlocked != null));
    }
  }

  /**
   * Minimal interpreter whose close() takes its own monitor, like RemoteInterpreter does via
   * getOrCreateInterpreterProcess(). It signals when the close worker reaches the monitor so the
   * test can force the interleaving deterministically.
   */
  private static class LockProbeInterpreter extends Interpreter {

    final CountDownLatch closeReached = new CountDownLatch(1);
    volatile Thread worker;

    LockProbeInterpreter(Properties properties) {
      super(properties);
    }

    @Override
    public void close() {
      worker = Thread.currentThread();
      closeReached.countDown();
      synchronized (this) {
      }
    }

    @Override
    public void open() {
    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) {
    }

    @Override
    public FormType getFormType() {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) {
      return 0;
    }
  }
}
