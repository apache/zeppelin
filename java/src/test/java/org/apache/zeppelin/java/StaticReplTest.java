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

package org.apache.zeppelin.java;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintStream;

public class StaticReplTest {

  @Test
  void shouldRestoreSystemStreamsWhenCompilationThrows(){
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;

    JavaCompiler compiler = mock(JavaCompiler.class);
    CompilationTask task = mock(CompilationTask.class);

    when(compiler.getTask(any(), any(), any(), any(), any(), any()))
        .thenReturn(task);
      
    when(task.call())
        .thenThrow(new RuntimeException("Compilation failed unexpectedly"));
      
    String code = "public class TestClass {"
        + " public static void main(String[] args) {}"
        + "}";
    
    try {
      assertThrows(RuntimeException.class, () -> StaticRepl.execute("TestClass", code, compiler));
      
      assertSame(originalOut, System.out);
      assertSame(originalErr, System.err);

    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
      
  }
    
}
