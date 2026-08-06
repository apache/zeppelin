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

package org.apache.zeppelin.interpreter.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.Test;

public class InterpreterLauncherTest {

  @Test
  public void testEscapeSpecialCharacters() {
    String cmd = "{}.";
    assertEquals("\\{\\}\\.", InterpreterLauncher.escapeSpecialCharacter(cmd));
  }

  @Test
  void launchUsesPluginClassLoaderAsContextAndRestoresThePreviousOne() throws Exception {
    InterpreterLauncher launcher = new InterpreterLauncher(ZeppelinConfiguration.load(), null) {
      @Override
      public InterpreterClient launchDirectly(InterpreterLaunchContext context)
          throws IOException {
        assertSame(getClass().getClassLoader(),
            Thread.currentThread().getContextClassLoader());
        return null;
      }
    };
    Thread thread = Thread.currentThread();
    ClassLoader previousClassLoader = thread.getContextClassLoader();
    try (URLClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null)) {
      thread.setContextClassLoader(emptyClassLoader);

      launcher.launch(null);

      assertSame(emptyClassLoader, thread.getContextClassLoader());
    } finally {
      thread.setContextClassLoader(previousClassLoader);
    }
  }
}
