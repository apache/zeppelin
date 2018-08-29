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

package org.apache.zeppelin.pig;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.pig.PigServer;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.tools.pigscript.parser.ParseException;
import org.apache.pig.tools.pigstats.PigStats;
import org.apache.pig.tools.pigstats.ScriptState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;

/**
 * Pig interpreter for Zeppelin.
 */
public class PigInterpreter extends BasePigInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(PigInterpreter.class);

  private PigServer pigServer;
  private boolean includeJobStats = false;

  public PigInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    String execType = getProperty("zeppelin.pig.execType");
    if (execType == null) {
      execType = "mapreduce";
    }
    String includeJobStats = getProperty("zeppelin.pig.includeJobStats");
    if (includeJobStats != null) {
      this.includeJobStats = Boolean.parseBoolean(includeJobStats);
    }
    try {
      pigServer = new PigServer(execType);
      for (Map.Entry entry : getProperties().entrySet()) {
        if (!entry.getKey().toString().startsWith("zeppelin.")) {
          pigServer.getPigContext().getProperties().setProperty(entry.getKey().toString(),
              entry.getValue().toString());
        }
      }
    } catch (IOException e) {
      LOGGER.error("Fail to initialize PigServer", e);
      throw new RuntimeException("Fail to initialize PigServer", e);
    }
  }

  @Override
  public void close() {
    pigServer = null;
  }


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    // remember the origial stdout, because we will redirect stdout to capture
    // the pig dump output.
    PrintStream originalStdOut = System.out;
    ByteArrayOutputStream bytesOutput = new ByteArrayOutputStream();
    File tmpFile = null;
    try {
      pigServer.setJobName(createJobName(cmd, contextInterpreter));
      tmpFile = PigUtils.createTempPigScript(cmd);
      System.setOut(new PrintStream(bytesOutput));
      // each thread should its own ScriptState & PigStats
      ScriptState.start(pigServer.getPigContext().getExecutionEngine().instantiateScriptState());
      // reset PigStats, otherwise you may get the PigStats of last job in the same thread
      // because PigStats is ThreadLocal variable
      PigStats.start(pigServer.getPigContext().getExecutionEngine().instantiatePigStats());
      PigScriptListener scriptListener = new PigScriptListener();
      ScriptState.get().registerListener(scriptListener);
      listenerMap.put(contextInterpreter.getParagraphId(), scriptListener);
      pigServer.registerScript(tmpFile.getAbsolutePath());
    } catch (IOException e) {
      // 1. catch FrontendException, FrontendException happens in the query compilation phase.
      // 2. catch ParseException for syntax error
      // 3. PigStats, This is execution error
      // 4. Other errors.
      if (e instanceof FrontendException) {
        FrontendException fe = (FrontendException) e;
        if (!fe.getMessage().contains("Backend error :")) {
          // If the error message contains "Backend error :", that means the exception is from
          // backend.
          LOGGER.error("Fail to run pig script.", e);
          return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
        }
      }
      if (e.getCause() instanceof ParseException) {
        return new InterpreterResult(Code.ERROR, e.getCause().getMessage());
      }
      PigStats stats = PigStats.get();
      if (stats != null) {
        String errorMsg = stats.getDisplayString();
        if (errorMsg != null) {
          LOGGER.error("Fail to run pig script, " + errorMsg);
          return new InterpreterResult(Code.ERROR, errorMsg);
        }
      }
      LOGGER.error("Fail to run pig script.", e);
      return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
    } finally {
      System.setOut(originalStdOut);
      listenerMap.remove(contextInterpreter.getParagraphId());
      if (tmpFile != null) {
        tmpFile.delete();
      }
    }
    StringBuilder outputBuilder = new StringBuilder();
    PigStats stats = PigStats.get();
    if (stats != null && includeJobStats) {
      String jobStats = stats.getDisplayString();
      if (jobStats != null) {
        outputBuilder.append(jobStats);
      }
    }
    outputBuilder.append(bytesOutput.toString());
    return new InterpreterResult(Code.SUCCESS, outputBuilder.toString());
  }


  public PigServer getPigServer() {
    return pigServer;
  }

}

