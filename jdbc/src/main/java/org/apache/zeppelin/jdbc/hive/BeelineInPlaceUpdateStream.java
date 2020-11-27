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

package org.apache.zeppelin.jdbc.hive;


import org.apache.hadoop.hive.common.log.InPlaceUpdate;
import org.apache.hadoop.hive.common.log.ProgressMonitor;
import org.apache.hive.jdbc.logs.InPlaceUpdateStream;
import org.apache.hive.service.rpc.thrift.TJobExecutionStatus;
import org.apache.hive.service.rpc.thrift.TProgressUpdateResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.List;

/**
 * This class is copied from hive project. It is for the purpose of print job progress.
 */
public class BeelineInPlaceUpdateStream implements InPlaceUpdateStream {
  private static final Logger LOGGER = LoggerFactory.getLogger(BeelineInPlaceUpdateStream.class);

  private InPlaceUpdate inPlaceUpdate;
  private EventNotifier notifier;
  private long lastUpdateTimestamp;

  public BeelineInPlaceUpdateStream(PrintStream out,
                                    InPlaceUpdateStream.EventNotifier notifier) {
    this.inPlaceUpdate = new InPlaceUpdate(out);
    this.notifier = notifier;
  }

  @Override
  public void update(TProgressUpdateResp response) {
    if (response == null || response.getStatus().equals(TJobExecutionStatus.NOT_AVAILABLE)) {
      /*
        we set it to completed if there is nothing the server has to report
        for example, DDL statements
      */
      notifier.progressBarCompleted();
    } else if (notifier.isOperationLogUpdatedAtLeastOnce()) {
      /*
        try to render in place update progress bar only if the operations logs is update at
        least once
        as this will hopefully allow printing the metadata information like query id,
        application id
        etc. have to remove these notifiers when the operation logs get merged into
        GetOperationStatus
      */
      lastUpdateTimestamp = System.currentTimeMillis();
      LOGGER.info("update progress: " + response.getProgressedPercentage());
      inPlaceUpdate.render(new ProgressMonitorWrapper(response));
    }
  }

  public long getLastUpdateTimestamp() {
    return lastUpdateTimestamp;
  }

  @Override
  public EventNotifier getEventNotifier() {
    return notifier;
  }

  static class ProgressMonitorWrapper implements ProgressMonitor {
    private TProgressUpdateResp response;

    ProgressMonitorWrapper(TProgressUpdateResp response) {
      this.response = response;
    }

    @Override
    public List<String> headers() {
      return response.getHeaderNames();
    }

    @Override
    public List<List<String>> rows() {
      return response.getRows();
    }

    @Override
    public String footerSummary() {
      return response.getFooterSummary();
    }

    @Override
    public long startTime() {
      return response.getStartTime();
    }

    @Override
    public String executionStatus() {
      throw new UnsupportedOperationException(
              "This should never be used for anything. All the required data is " +
                      "available via other methods"
      );
    }

    @Override
    public double progressedPercentage() {
      return response.getProgressedPercentage();
    }
  }
}

