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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class InterpreterCallbackRegistryTest {

  @Test
  public void testBasic() {
    final AtomicInteger onRegister = new AtomicInteger(0);
    final AtomicInteger onUnregister = new AtomicInteger(0);
    final String PRE_EXEC = InterpreterCallbackRegistry.PRE_EXEC;
    final String POST_EXEC = InterpreterCallbackRegistry.POST_EXEC;
    final String noteId = "note";
    final String replName = "repl";
    final String preExecCallback = "pre";
    final String postExecCallback = "post";
    InterpreterCallbackRegistry registry = new InterpreterCallbackRegistry("intpId",
        new InterpreterCallbackRegistryListener() {

          @Override
          public void onRegister(String interpreterGroupId, String noteId, String replName,
                                 String event, String cmd) {
            onRegister.incrementAndGet();
          }

          @Override
          public void onUnregister(String interpreterGroupId, String noteId, String replName,
                                  String event) {
            onUnregister.incrementAndGet();
          }
    });
    
    // Test register()
    registry.register(noteId, replName, PRE_EXEC, preExecCallback);
    registry.register(noteId, replName, POST_EXEC, postExecCallback);
    assertEquals(2, onRegister.get());
    assertEquals(0, onUnregister.get());

    // Test get()
    assertEquals(registry.get(noteId, replName, PRE_EXEC), preExecCallback);
    assertEquals(registry.get(noteId, replName, POST_EXEC), postExecCallback);

    // Test Unregister
    registry.unregister(noteId, replName, PRE_EXEC);
    assertNull(registry.get(noteId, replName, PRE_EXEC));
    assertEquals(1, onUnregister.get());
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testValidEventCode() {
    InterpreterCallbackRegistry registry = new InterpreterCallbackRegistry("intpId", null);
    
    // Test that only valid event codes ("pre_exec", "post_exec") are accepted
    registry.register("foo", "bar", "baz", "whatever");
  }

}
