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
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.JobID;
import org.apache.flink.core.execution.JobClient;
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

  private Map<String, JobClient> jobs = new HashMap<>();
  private ConcurrentHashMap<JobID, FlinkJobProgressPoller> jobProgressPollerMap =
          new ConcurrentHashMap<>();
  private FlinkZeppelinContext z;
  private String flinkWebUI;

  public JobManager(FlinkZeppelinContext z,
                    String flinkWebUI) {
    this.z = z;
    this.flinkWebUI = flinkWebUI;
  }

  public void addJob(InterpreterContext context, JobClient jobClient) {
    String paragraphId = context.getParagraphId();
    JobClient previousJobClient = this.jobs.put(paragraphId, jobClient);
    FlinkJobProgressPoller thread = new FlinkJobProgressPoller(flinkWebUI, jobClient.getJobID(), context);
    thread.setName("JobProgressPoller-Thread-" + paragraphId);
    thread.start();
    this.jobProgressPollerMap.put(jobClient.getJobID(), thread);
    if (previousJobClient != null) {
      LOGGER.warn("There's another Job {} that is associated with paragraph {}",
              jobClient.getJobID(), paragraphId);
    }
  }

  public void removeJob(String paragraphId) {
    LOGGER.info("Remove job in paragraph: " + paragraphId);
    JobClient jobClient = this.jobs.remove(paragraphId);
    if (jobClient == null) {
      LOGGER.warn("Unable to remove job, because no job is associated with paragraph: "
              + paragraphId);
      return;
    }
    FlinkJobProgressPoller jobProgressPoller =
            this.jobProgressPollerMap.remove(jobClient.getJobID());
    jobProgressPoller.cancel();
    jobProgressPoller.interrupt();
  }

  public void sendFlinkJobUrl(InterpreterContext context) {
    JobClient jobClient = jobs.get(context.getParagraphId());
    if (jobClient != null) {
      String jobUrl = flinkWebUI + "#/job/" + jobClient.getJobID();
      Map<String, String> infos = new HashMap<>();
      infos.put("jobUrl", jobUrl);
      infos.put("label", "FLINK JOB");
      infos.put("tooltip", "View in Flink web UI");
      infos.put("noteId", context.getNoteId());
      infos.put("paraId", context.getParagraphId());
      context.getIntpEventClient().onParaInfosReceived(infos);
    } else {
      LOGGER.warn("No job is associated with paragraph: " + context.getParagraphId());
    }
  }

  public int getJobProgress(String paragraphId) {
    JobClient jobClient = this.jobs.get(paragraphId);
    if (jobClient == null) {
      LOGGER.warn("Unable to get job progress for paragraph: " + paragraphId +
              ", because no job is associated with this paragraph");
      return 0;
    }
    FlinkJobProgressPoller jobProgressPoller = this.jobProgressPollerMap.get(jobClient.getJobID());
    if (jobProgressPoller == null) {
      LOGGER.warn("Unable to get job progress for paragraph: " + paragraphId +
              ", because no job progress is associated with this jobId: " + jobClient.getJobID());
      return 0;
    }
    return jobProgressPoller.getProgress();
  }

  public void cancelJob(InterpreterContext context) throws InterpreterException {
    LOGGER.info("Canceling job associated of paragraph: "+ context.getParagraphId());
    JobClient jobClient = this.jobs.get(context.getParagraphId());
    if (jobClient == null) {
      LOGGER.warn("Unable to remove Job from paragraph {} as no job associated to this paragraph",
              context.getParagraphId());
      return;
    }

    try {
      String savepointDir = context.getLocalProperties().get("savepointDir");
      if (StringUtils.isBlank(savepointDir)) {
        LOGGER.info("Trying to cancel job of paragraph {}", context.getParagraphId());
        jobClient.cancel();
      } else {
        LOGGER.info("Trying to stop job of paragraph {} with save point dir: {}",
                context.getParagraphId(), savepointDir);
        String savePointPath = jobClient.stopWithSavepoint(true, savepointDir).get();
        z.angularBind(context.getParagraphId() + "_savepointpath", savePointPath);
        LOGGER.info("Job {} of paragraph {} is stopped with save point path: {}",
                jobClient.getJobID(), context.getParagraphId(), savePointPath);
      }
    } catch (Exception e) {
      String errorMessage = String.format("Fail to cancel job %s that is associated " +
              "with paragraph %s", jobClient.getJobID(), context.getParagraphId());
      LOGGER.warn(errorMessage, e);
      throw new InterpreterException(errorMessage, e);
    } finally {
      FlinkJobProgressPoller jobProgressPoller = jobProgressPollerMap.remove(jobClient.getJobID());
      if (jobProgressPoller != null) {
        jobProgressPoller.cancel();
        jobProgressPoller.interrupt();
      }
      this.jobs.remove(context.getParagraphId());
    }
  }

  public void shutdown() {
    for (FlinkJobProgressPoller jobProgressPoller : jobProgressPollerMap.values()) {
      jobProgressPoller.cancel();
    }
  }

  class FlinkJobProgressPoller extends Thread {

    private String flinkWebUI;
    private JobID jobId;
    private InterpreterContext context;
    private boolean isStreamingInsertInto;
    private int progress;
    private AtomicBoolean running = new AtomicBoolean(true);
    private boolean isFirstPoll = true;

    FlinkJobProgressPoller(String flinkWebUI, JobID jobId, InterpreterContext context) {
      this.flinkWebUI = flinkWebUI;
      this.jobId = jobId;
      this.context = context;
      this.isStreamingInsertInto = context.getLocalProperties().containsKey("flink.streaming.insert_into");
    }

    @Override
    public void run() {
      while (!Thread.currentThread().isInterrupted() && running.get()) {
        try {
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
            LOGGER.debug("Progress: " + this.progress);
          }
          String jobState = rootNode.getObject().getString("state");
          if (jobState.equalsIgnoreCase("finished")) {
            break;
          }
          synchronized (running) {
            running.wait(1000);
          }
          if (isStreamingInsertInto) {
            if (isFirstPoll) {
              StringBuilder builder = new StringBuilder("%angular ");
              builder.append("<h1>Duration: {{duration}} seconds");
              builder.append("\n%text ");
              context.out.clear(false);
              context.out.write(builder.toString());
              context.out.flush();
              isFirstPoll = false;
            }
            context.getAngularObjectRegistry().add("duration",
                    rootNode.getObject().getLong("duration") / 1000,
                    context.getNoteId(),
                    context.getParagraphId());
          }
        } catch (Exception e) {
          LOGGER.error("Fail to poll flink job progress via rest api", e);
        }
      }
    }

      public void cancel () {
        this.running.set(false);
        synchronized (running) {
          running.notify();
        }
      }

      public int getProgress () {
        return progress;
      }
    }
  }
