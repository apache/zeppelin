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

package org.apache.zeppelin.flink;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.scala.ExecutionEnvironment;
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobManager {

  private static Logger LOGGER = LoggerFactory.getLogger(JobManager.class);

  private Map<String, JobID> jobs = new HashMap<>();
  private Map<String, String> savePointMap = new HashMap<>();
  private ConcurrentHashMap<JobID, FlinkJobProgressPoller> jobProgressPollerMap =
          new ConcurrentHashMap<>();
  private ExecutionEnvironment env;
  private StreamExecutionEnvironment senv;
  private FlinkZeppelinContext z;
  private String flinkWebUI;

  public JobManager(ExecutionEnvironment env,
                    StreamExecutionEnvironment senv,
                    FlinkZeppelinContext z,
                    String flinkWebUI) {
    this.env = env;
    this.senv = senv;
    this.z = z;
    this.flinkWebUI = flinkWebUI;
  }

  public void addJob(String paragraphId, JobID jobId) {
    JobID previousJobId = this.jobs.put(paragraphId, jobId);
    FlinkJobProgressPoller thread = new FlinkJobProgressPoller(flinkWebUI, jobId);
    thread.start();
    this.jobProgressPollerMap.put(jobId, thread);
    if (previousJobId != null) {
      LOGGER.warn("There's another Job {} that is associated with paragraph {}",
              jobId, paragraphId);
    }
  }

  public void removeJob(String paragraphId) {
    JobID jobID = this.jobs.remove(paragraphId);
    if (jobID == null) {
      LOGGER.warn("Unable to remove job, because no job is associated with paragraph: "
              + paragraphId);
      return;
    }
    FlinkJobProgressPoller jobProgressPoller = this.jobProgressPollerMap.remove(jobID);
    jobProgressPoller.cancel();
  }

  public int getJobProgress(String paragraphId) {
    JobID jobId = this.jobs.get(paragraphId);
    if (jobId == null) {
      LOGGER.warn("Unable to get job progress for paragraph: " + paragraphId +
              ", because no job is associated with this paragraph");
      return 0;
    }
    FlinkJobProgressPoller jobProgressPoller = this.jobProgressPollerMap.get(jobId);
    if (jobProgressPoller == null) {
      LOGGER.warn("Unable to get job progress for paragraph: " + paragraphId +
              ", because no job progress is associated with this jobId: " + jobId);
      return 0;
    }
    return jobProgressPoller.getProgress();
  }

  public void cancelJob(InterpreterContext context) throws InterpreterException {
    JobID jobId = this.jobs.remove(context.getParagraphId());
    if (jobId == null) {
      LOGGER.warn("Unable to remove Job from paragraph {}", context.getParagraphId());
      return;
    }

    try {
      //this.env.cancel(jobId);
    } catch (Exception e) {
      String errorMessage = String.format("Fail to cancel job %s that is associated " +
              "with paragraph %s", jobId, context.getParagraphId());
      LOGGER.warn(errorMessage, e);
      throw new InterpreterException(errorMessage, e);
    }

    FlinkJobProgressPoller jobProgressPoller = jobProgressPollerMap.remove(jobId);
    jobProgressPoller.interrupt();
  }

  class FlinkJobProgressPoller extends Thread {

    private String flinkWebUI;
    private JobID jobId;
    private int progress;
    private AtomicBoolean running = new AtomicBoolean(true);

    FlinkJobProgressPoller(String flinkWebUI, JobID jobId) {
      this.flinkWebUI = flinkWebUI;
      this.jobId = jobId;
    }

    @Override
    public void run() {
      try {
        while (!Thread.currentThread().isInterrupted() && running.get()) {
          JsonNode rootNode = Unirest.get(flinkWebUI + "/jobs/" + jobId.toString())
                  .asJson().getBody();
          JSONArray vertices = rootNode.getObject().getJSONArray("vertices");
          int totalTasks = 0;
          int finishedTasks = 0;
          for (int i = 0; i < vertices.length(); ++i) {
            JSONObject vertex = vertices.getJSONObject(i);
            totalTasks += vertex.getInt("parallelism");
            finishedTasks += vertex.getJSONObject("tasks").getInt("FINISHED");
          }
          LOGGER.debug("Total tasks:" + totalTasks);
          LOGGER.debug("Finished tasks:" + finishedTasks);
          if (finishedTasks != 0) {
            this.progress = finishedTasks * 100 / totalTasks;
          }
          String jobState = rootNode.getObject().getString("state");
          if (jobState.equalsIgnoreCase("finished")) {
            break;
          }
          synchronized (running) {
            running.wait(1000);
          }
        }
      } catch (Exception e) {
        LOGGER.error("Fail to poll flink job progress via rest api", e);
      }
    }

    public void cancel() {
      this.running.set(false);
      synchronized (running) {
        running.notify();
      }
    }

    public int getProgress() {
      return progress;
    }
  }
}
