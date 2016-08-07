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

package org.apache.zeppelin.shell.security;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;


/***
 * Shell security helper
 */
public class ShellSecurityImpl {

  private static Logger LOGGER = LoggerFactory.getLogger(ShellSecurityImpl.class);

  public static void createSecureConfiguration(Properties properties, String shell) {

    String authType = properties.getProperty("zeppelin.shell.auth.type")
      .trim().toUpperCase();

    switch (authType) {
        case "KERBEROS":
          CommandLine cmdLine = CommandLine.parse(shell);
          cmdLine.addArgument("-c", false);
          String kinitCommand = String.format("kinit -k -t %s %s",
            properties.getProperty("zeppelin.shell.keytab.location"),
            properties.getProperty("zeppelin.shell.principal"));
          cmdLine.addArgument(kinitCommand, false);
          DefaultExecutor executor = new DefaultExecutor();

          try {
            int exitVal = executor.execute(cmdLine);
          } catch (Exception e) {
            LOGGER.error("Unable to run kinit for zeppelin user " + kinitCommand, e);
            throw new InterpreterException(e);
          }
    }
  }
}
