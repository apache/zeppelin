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

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.types.Row;
import org.apache.zeppelin.flink.FlinkShims;
import org.apache.zeppelin.flink.JobManager;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.SingleRowInterpreterResult;
import org.apache.zeppelin.tabledata.TableDataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class SingleRowStreamSqlJob extends AbstractStreamSqlJob {

  private static Logger LOGGER = LoggerFactory.getLogger(SingleRowStreamSqlJob.class);

  private Row latestRow;
  private String template;
  private boolean isFirstRefresh = true;

  public SingleRowStreamSqlJob(StreamExecutionEnvironment senv,
                               TableEnvironment stenv,
                               JobManager jobManager,
                               InterpreterContext context,
                               int defaultParallelism,
                               FlinkShims flinkShims) {
    super(senv, stenv, jobManager, context, defaultParallelism, flinkShims);
    this.template = context.getLocalProperties().getOrDefault("template", "{0}");
  }

  @Override
  protected String getType() {
    return "single";
  }

  protected void processInsert(Row row) {
    LOGGER.debug("processInsert: " + row.toString());
    latestRow = row;
  }

  @Override
  protected void processDelete(Row row) {
    //LOGGER.debug("Ignore delete");
  }

  @Override
  protected String buildResult() {
    SingleRowInterpreterResult singleRowResult =
            new SingleRowInterpreterResult(rowToList(latestRow), template, context);
    singleRowResult.pushAngularObjects();
    return singleRowResult.toAngular();
  }

  @Override
  protected void refresh(InterpreterContext context) throws Exception {
    if (latestRow == null) {
      LOGGER.warn("Skip RefreshTask as no data available");
      return;
    }
    SingleRowInterpreterResult singleRowResult =
            new SingleRowInterpreterResult(rowToList(latestRow), template, context);
    if (isFirstRefresh) {
      context.out().clear(false);
      context.out.write(singleRowResult.toAngular());
      context.out.flush();
      // should checkpoint the html output, otherwise frontend won't display the output
      // after recovering.
      context.getIntpEventClient().checkpointOutput(context.getNoteId(), context.getParagraphId());
      isFirstRefresh = false;
    }

    singleRowResult.pushAngularObjects();
  }

  private List rowToList(Row row) {
    List list = new ArrayList<>();
    for (int i = 0; i < row.getArity(); i++) {
      list.add(row.getField(i));
    }
    return list;
  }
}
