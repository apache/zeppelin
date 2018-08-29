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

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class BaseZeppelinContextTest {

  @Test
  public void testHooks() throws InvalidHookException {
    InterpreterHookRegistry hookRegistry = new InterpreterHookRegistry();
    TestZeppelinContext z = new TestZeppelinContext(hookRegistry, 10);
    InterpreterContext context = InterpreterContext.builder()
        .setNoteId("note_1")
        .setNoteName("note_name_1")
        .setParagraphId("paragraph_1")
        .setInterpreterClassName("Test1Interpreter")
        .setReplName("test1")
        .build();
    z.setInterpreterContext(context);

    // get note name via InterpreterContext
    String note_name = z.getInterpreterContext().getNoteName();
    assertEquals(
            String.format("Actual note name: %s, but expected %s", note_name, "note_name_1"),
            "note_name_1",
            note_name
    );

    // register global hook for current interpreter
    z.registerHook(InterpreterHookRegistry.HookType.PRE_EXEC.getName(), "pre_cmd");
    z.registerHook(InterpreterHookRegistry.HookType.POST_EXEC.getName(), "post_cmd");
    assertEquals("pre_cmd", hookRegistry.get(null, "Test1Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals("post_cmd", hookRegistry.get(null, "Test1Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    z.unregisterHook(InterpreterHookRegistry.HookType.PRE_EXEC.getName());
    z.unregisterHook(InterpreterHookRegistry.HookType.POST_EXEC.getName());
    assertEquals(null, hookRegistry.get(null, "Test1Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals(null, hookRegistry.get(null, "Test1Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    // register global hook for interpreter test2
    z.registerHook(InterpreterHookRegistry.HookType.PRE_EXEC.getName(), "pre_cmd2", "test2");
    z.registerHook(InterpreterHookRegistry.HookType.POST_EXEC.getName(), "post_cmd2", "test2");
    assertEquals("pre_cmd2", hookRegistry.get(null, "Test2Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals("post_cmd2", hookRegistry.get(null, "Test2Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    z.unregisterHook(InterpreterHookRegistry.HookType.PRE_EXEC.getName(), "test2");
    z.unregisterHook(InterpreterHookRegistry.HookType.POST_EXEC.getName(), "test2");
    assertEquals(null, hookRegistry.get(null, "Test2Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals(null, hookRegistry.get(null, "Test2Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    // register hook for note_1 and current interpreter
    z.registerNoteHook(InterpreterHookRegistry.HookType.PRE_EXEC.getName(), "pre_cmd", "note_1");
    z.registerNoteHook(InterpreterHookRegistry.HookType.POST_EXEC.getName(), "post_cmd", "note_1");
    assertEquals("pre_cmd", hookRegistry.get("note_1", "Test1Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals("post_cmd", hookRegistry.get("note_1", "Test1Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    z.unregisterNoteHook("note_1", InterpreterHookRegistry.HookType.PRE_EXEC.getName(), "test1");
    z.unregisterNoteHook("note_1", InterpreterHookRegistry.HookType.POST_EXEC.getName(), "test1");
    assertEquals(null, hookRegistry.get("note_1", "Test1Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals(null, hookRegistry.get("note_1", "Test1Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    // register hook for note_1 and interpreter test2
    z.registerNoteHook(InterpreterHookRegistry.HookType.PRE_EXEC.getName(),
        "pre_cmd2", "note_1", "test2");
    z.registerNoteHook(InterpreterHookRegistry.HookType.POST_EXEC.getName(),
        "post_cmd2", "note_1", "test2");
    assertEquals("pre_cmd2", hookRegistry.get("note_1", "Test2Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals("post_cmd2", hookRegistry.get("note_1", "Test2Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));

    z.unregisterNoteHook("note_1", InterpreterHookRegistry.HookType.PRE_EXEC.getName(), "test2");
    z.unregisterNoteHook("note_1", InterpreterHookRegistry.HookType.POST_EXEC.getName(), "test2");
    assertEquals(null, hookRegistry.get("note_1", "Test2Interpreter",
        InterpreterHookRegistry.HookType.PRE_EXEC.getName()));
    assertEquals(null, hookRegistry.get("note_1", "Test2Interpreter",
        InterpreterHookRegistry.HookType.POST_EXEC.getName()));
  }


  public static class TestZeppelinContext extends BaseZeppelinContext {

    public TestZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
      super(hooks, maxResult);
    }

    @Override
    public Map<String, String> getInterpreterClassMap() {
      Map<String, String> map = new HashMap<>();
      map.put("test1", "Test1Interpreter");
      map.put("test2", "Test2Interpreter");
      return map;
    }

    @Override
    public List<Class> getSupportedClasses() {
      return null;
    }

    @Override
    protected String showData(Object obj) {
      return null;
    }
  }


}
