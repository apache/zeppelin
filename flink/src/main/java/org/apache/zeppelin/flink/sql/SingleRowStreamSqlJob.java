/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink.sql;

import com.google.common.collect.Lists;
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment;
import org.apache.flink.table.api.scala.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SingleRowStreamSqlJob extends AbstractStreamSqlJob {

  private static Logger LOGGER = LoggerFactory.getLogger(SingleRowStreamSqlJob.class);

  private Row latestRow;
  private String template;

  public SingleRowStreamSqlJob(StreamExecutionEnvironment senv,
                               StreamTableEnvironment stenv,
                               InterpreterContext context,
                               int defaultParallelism) {
    super(senv, stenv, context, defaultParallelism);
    this.template = context.getLocalProperties().getOrDefault("template", "{0}");
  }

  @Override
  protected String getType() {
    return "single";
  }

  @Override
  protected List<String> getValidLocalProperties() {
    return Lists.newArrayList("type", "parallelism",
            "refreshInterval", "template", "enableSavePoint", "runWithSavePoint");
  }

  protected void processInsert(Row row) {
    LOGGER.debug("processInsert: " + row.toString());
    latestRow = row;
  }

  @Override
  protected void processDelete(Row row) {
    LOGGER.debug("Ignore delete");
  }

  @Override
  protected void refresh(InterpreterContext context) throws Exception {
    if (latestRow == null) {
      LOGGER.warn("Skip RefreshTask as no data available");
      return;
    }
    context.out().clear();
    context.out.write("%html\n");
    String outputText = template;
    for (int i = 0; i < latestRow.getArity(); ++i) {
      outputText = outputText.replace("{" + i + "}", latestRow.getField(i).toString());
    }
    LOGGER.debug("SingleRow Output: " + outputText);
    context.out.write(outputText);
    context.out.flush();
  }
}
