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

package org.apache.zeppelin;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandExecutor {

  public final static Logger LOG = LoggerFactory.getLogger(CommandExecutor.class);

  public enum IGNORE_ERRORS {
    TRUE,
    FALSE
  }

  public static int NORMAL_EXIT = 0;

  private static IGNORE_ERRORS DEFAULT_BEHAVIOUR_ON_ERRORS = IGNORE_ERRORS.TRUE;

  public static Object executeCommandLocalHost(String[] command, boolean printToConsole, ProcessData.Types_Of_Data type, IGNORE_ERRORS ignore_errors) {
    List<String> subCommandsAsList = new ArrayList<String>(Arrays.asList(command));
    String mergedCommand = StringUtils.join(subCommandsAsList, " ");

    LOG.info("Sending command \"" + mergedCommand + "\" to localhost");

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    Process process = null;
    try {
      process = processBuilder.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    ProcessData data_of_process = new ProcessData(process, printToConsole);
    Object output_of_process = data_of_process.getData(type);
    int exit_code = data_of_process.getExitCodeValue();

    if (!printToConsole)
      LOG.trace(output_of_process.toString());
    else
      LOG.debug(output_of_process.toString());
    if (ignore_errors == IGNORE_ERRORS.FALSE && exit_code != NORMAL_EXIT) {
      LOG.error(String.format("*********************Command '%s' failed with exitcode %s *********************", mergedCommand, exit_code));
    }
    return output_of_process;
  }

  public static Object executeCommandLocalHost(String command, boolean printToConsole, ProcessData.Types_Of_Data type) {
    return executeCommandLocalHost(new String[]{"bash", "-c", command}, printToConsole, type, DEFAULT_BEHAVIOUR_ON_ERRORS);
  }

}
