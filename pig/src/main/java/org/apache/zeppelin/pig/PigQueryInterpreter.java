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


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.pig.PigServer;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.tools.pigstats.PigStats;
import org.apache.pig.tools.pigstats.ScriptState;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 *
 */
public class PigQueryInterpreter extends BasePigInterpreter {

  private static Logger LOGGER = LoggerFactory.getLogger(PigQueryInterpreter.class);
  private PigServer pigServer;
  private int maxResult;

  public PigQueryInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {
    pigServer = getPigInterpreter().getPigServer();
    maxResult = Integer.parseInt(getProperty("zeppelin.pig.maxResult"));
  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    // '-' is invalid for pig alias
    String alias = "paragraph_" + context.getParagraphId().replace("-", "_");
    String[] lines = st.split("\n");
    List<String> queries = new ArrayList<>();
    for (int i = 0; i < lines.length; ++i) {
      if (i == lines.length - 1) {
        lines[i] = alias + " = " + lines[i];
      }
      queries.add(lines[i]);
    }

    StringBuilder resultBuilder = new StringBuilder("%table ");
    try {
      File tmpScriptFile = PigUtils.createTempPigScript(queries);
      // each thread should its own ScriptState & PigStats
      ScriptState.start(pigServer.getPigContext().getExecutionEngine().instantiateScriptState());
      // reset PigStats, otherwise you may get the PigStats of last job in the same thread
      // because PigStats is ThreadLocal variable
      PigStats.start(pigServer.getPigContext().getExecutionEngine().instantiatePigStats());
      PigScriptListener scriptListener = new PigScriptListener();
      ScriptState.get().registerListener(scriptListener);
      listenerMap.put(context.getParagraphId(), scriptListener);
      pigServer.registerScript(tmpScriptFile.getAbsolutePath());
      Schema schema = pigServer.dumpSchema(alias);
      boolean schemaKnown = (schema != null);
      if (schemaKnown) {
        for (int i = 0; i < schema.size(); ++i) {
          Schema.FieldSchema field = schema.getField(i);
          resultBuilder.append(field.alias);
          if (i != schema.size() - 1) {
            resultBuilder.append("\t");
          }
        }
        resultBuilder.append("\n");
      }
      Iterator<Tuple> iter = pigServer.openIterator(alias);
      boolean firstRow = true;
      int index = 0;
      while (iter.hasNext() && index <= maxResult) {
        index++;
        Tuple tuple = iter.next();
        if (firstRow && !schemaKnown) {
          for (int i = 0; i < tuple.size(); ++i) {
            resultBuilder.append("c_" + i + "\t");
          }
          resultBuilder.append("\n");
          firstRow = false;
        }
        resultBuilder.append(StringUtils.join(tuple, "\t"));
        resultBuilder.append("\n");
      }
      if (index >= maxResult && iter.hasNext()) {
        resultBuilder.append("\n<font color=red>Results are limited by " + maxResult + ".</font>");
      }
    } catch (IOException e) {
      // Extract error in the following order
      // 1. catch FrontendException, FrontendException happens in the query compilation phase.
      // 2. PigStats, This is execution error
      // 3. Other errors.
      if (e instanceof FrontendException) {
        FrontendException fe = (FrontendException) e;
        if (!fe.getMessage().contains("Backend error :")) {
          LOGGER.error("Fail to run pig query.", e);
          return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
        }
      }
      PigStats stats = PigStats.get();
      if (stats != null) {
        String errorMsg = PigUtils.extactJobStats(stats);
        if (errorMsg != null) {
          return new InterpreterResult(Code.ERROR, errorMsg);
        }
      }
      LOGGER.error("Fail to run pig query.", e);
      return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
    } finally {
      listenerMap.remove(context.getParagraphId());
    }
    return new InterpreterResult(Code.SUCCESS, resultBuilder.toString());
  }

  @Override
  public PigServer getPigServer() {
    return this.pigServer;
  }

  private PigInterpreter getPigInterpreter() {
    LazyOpenInterpreter lazy = null;
    PigInterpreter pig = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(PigInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    pig = (PigInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return pig;
  }
}
