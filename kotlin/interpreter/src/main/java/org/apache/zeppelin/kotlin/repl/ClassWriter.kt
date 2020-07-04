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

package org.apache.zeppelin.kotlin.repl;

import org.jetbrains.kotlin.cli.common.repl.CompiledClassData;
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript;

/**
 *  Kotlin REPL compiler generates compiled classes consisting of
 * compiled in-memory module and some other classes.
 *  Spark may need saving them somewhere to send them to the executors,
 * so this class provides writing classes on disk.
 */
public class ClassWriter {
  private static Logger logger = LoggerFactory.getLogger(ClassWriter.class);

  private String outputDir;

  public ClassWriter(String outputDir) {
    this.outputDir = outputDir;
  }

  public void writeClasses(ReplCompileResult.CompiledClasses classes) {
    if (outputDir == null) {
      return;
    }

    for (CompiledClassData compiledClass: classes.getClasses()) {
      String filePath = compiledClass.getPath();
      if (!filePath.contains(File.separator)) {
        String classWritePath = outputDir + File.separator + filePath;
        writeClass(compiledClass.getBytes(), classWritePath);
      }
    }

    writeModuleInMemory(classes);
  }

  private void writeModuleInMemory(ReplCompileResult.CompiledClasses classes) {
    try {
      KJvmCompiledScript<?> compiledScript = Objects.requireNonNull(
              (KJvmCompiledScript<?>) classes.getData());

      KJvmCompiledModuleInMemory moduleInMemory = Objects.requireNonNull(
              (KJvmCompiledModuleInMemory) compiledScript.getCompiledModule());

      moduleInMemory.getCompilerOutputFiles().forEach((name, bytes) -> {
        if (name.contains("class")) {
          writeClass(bytes, outputDir + File.separator + name);
        }
      });
    } catch (ClassCastException | NullPointerException e) {
      logger.info("Compiled line #" + classes.getLineId().getNo() + "has no in-memory modules");
    }
  }

  private void writeClass(byte[] classBytes, String path) {
    try (FileOutputStream fos = new FileOutputStream(path);
      OutputStream out = new BufferedOutputStream(fos)) {
        out.write(classBytes);
        out.flush();
      } catch (IOException e) {
        logger.error(e.getMessage());
      }
    }
}
