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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;

public class ResultDataFileCleaner extends Thread {

  Logger logger = LoggerFactory.getLogger(ResultDataFileCleaner.class);

  String resultDataDir;

  public static ResultDataFileCleaner resultDataFileCleaner;
  public static synchronized void start(String resultDataDir) {

    if (resultDataFileCleaner == null) {
      resultDataFileCleaner = new ResultDataFileCleaner(resultDataDir);
      resultDataFileCleaner.start();
    } else if (!Objects.equals(resultDataFileCleaner.resultDataDir, resultDataDir)) {
      resultDataFileCleaner.resultDataDir = resultDataDir;
    }

    if (resultDataFileCleaner.isInterrupted()) {
      resultDataFileCleaner.start();
    }
  }

  @Override
  public void run() {
    long fourHour = 4 * 60 * 60 * 1000;
    logger.info("Result data file cleaner started.");

    while (true) {
      try {
        Thread.sleep(10 * 60 * 1000);
      } catch (InterruptedException e) {
        logger.info("Result data file cleaner stopped.");
        break;
      }
      long currentTime = System.currentTimeMillis();

      File file = new File(resultDataDir);
      if (!file.exists()) {
        logger.error(file + " does not exist.");
      } else if (!file.isDirectory()) {
        logger.error(file + " is not directory.");
      } else {
        boolean FILE_EXIST = false;
        File[] files = file.listFiles();
        if (files != null) {
          for (File eachFile: files) {
            if (eachFile.isDirectory()) {
              continue;
            }

            FILE_EXIST = true;
            if (currentTime - eachFile.lastModified() >= fourHour) {
              if (eachFile.delete()) {
                logger.info("Delete " + eachFile + " because of expired.");
              }
            }
          }
        }

        if (!FILE_EXIST) {
          resultDataFileCleaner.interrupt();
        }
      }
    }
  }

  public ResultDataFileCleaner(String resultDataDir) {
    this.resultDataDir = resultDataDir;
  }
}
