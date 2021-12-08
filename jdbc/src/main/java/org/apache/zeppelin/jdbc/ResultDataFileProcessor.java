/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.jdbc;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.System.getProperty;

public class ResultDataFileProcessor {

  Logger logger = LoggerFactory.getLogger(ResultDataFileProcessor.class);

  static final String RESULT_DATA_PATH = "zeppelin.result.data.path";

  String resultDataDir;
  Properties property;
  Map<Integer, File> map = new HashMap<>();

  public ResultDataFileProcessor(Properties property) {

    if (property == null) {
      property = new Properties();
    }
    this.property = property;

    prepare();
  }

  public synchronized void createFile(InterpreterContext context, int hashCode)
          throws IOException, InterruptedException {

    String resultFilePath = getFileName(context);
    File resultFile = new File(resultFilePath);
    if (resultFile.exists()) {
      if (!resultFile.delete()) {
        return;
      }
    }

    if (!resultFile.createNewFile()) {
      return;
    }

    map.put(hashCode, resultFile);
  }

  public File getFile(int hashCode) {
    File file = map.get(hashCode);
    map.remove(hashCode);
    return file;
  }

  public void deleteFile(InterpreterContext context) {
    String resultFilePath = getFileName(context);
    File resultFile = new File(resultFilePath);
    if (resultFile.exists()) {
      resultFile.delete();
    }
  }

  void prepare() {
    resultDataDir = getProperty(RESULT_DATA_PATH);
    if (resultDataDir == null) {
      String defaultBaseDir = System.getProperty("java.io.tmpdir");
      // set tmpdir to /tmp on MacOS, because docker can not share the /var folder which will
      // cause PythonDockerInterpreter fails.
      // https://stackoverflow.com/questions/45122459/docker-mounts-denied-the-paths-are-not-shared-
      // from-os-x-and-are-not-known
      if (System.getProperty("os.name", "").contains("Mac")) {
        defaultBaseDir = "/tmp";
      }
      resultDataDir = String.join(File.separator,
          defaultBaseDir, "zeppelin-" + getProperty("user.name"));
    }

    File file = new File(resultDataDir);
    if (!file.exists()) {
      if (!file.mkdir()) {
        logger.error("Can't make result directory: " + file);
      } else {
        logger.info("Created result directory: " + file);
      }
    }
  }

  public String getFileName(InterpreterContext context) {
    return String.join(File.separator, resultDataDir,
            context.getNoteId() + "_" + context.getParagraphId());
  }

  public String getResultDataDir() {
    return resultDataDir;
  }
}
