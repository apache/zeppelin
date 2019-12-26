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

package org.apache.zeppelin.submarine;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

public abstract class BaseInterpreterTest {

  @Before
  public abstract void setUp() throws InterpreterException;

  @After
  public abstract void tearDown() throws InterpreterException;

  protected InterpreterContext getIntpContext() {
    final AtomicInteger onAdd = new AtomicInteger(0);
    final AtomicInteger onUpdate = new AtomicInteger(0);
    final AtomicInteger onRemove = new AtomicInteger(0);
    AngularObjectRegistry registry = new AngularObjectRegistry("intpId",
        new AngularObjectRegistryListener() {

          @Override
          public void onAdd(String interpreterGroupId, AngularObject object) {
            onAdd.incrementAndGet();
          }

          @Override
          public void onUpdate(String interpreterGroupId, AngularObject object) {
            onUpdate.incrementAndGet();
          }

          @Override
          public void onRemove(String interpreterGroupId,
                               String name,
                               String noteId,
                               String paragraphId) {
            onRemove.incrementAndGet();
          }
        });

    AuthenticationInfo authenticationInfo = new AuthenticationInfo("user");

    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setNoteName("noteName")
        .setParagraphId("paragraphId")
        .setAuthenticationInfo(authenticationInfo)
        .setAngularObjectRegistry(registry)
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
        .build();
  }
}
