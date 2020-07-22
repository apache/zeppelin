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


package org.apache.zeppelin.client;

import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ParagraphResult {
  private String paragraphId;
  private Status status;
  private int progress;
  private List<Result> results;
  private List<String> jobUrls;

  public ParagraphResult(JSONObject paragraphJson) {
    this.paragraphId = paragraphJson.getString("id");
    this.status = Status.valueOf(paragraphJson.getString("status"));
    this.progress = paragraphJson.getInt("progress");
    this.results = new ArrayList<>();
    if (paragraphJson.has("results")) {
      JSONObject resultJson = paragraphJson.getJSONObject("results");
      JSONArray msgArray = resultJson.getJSONArray("msg");
      for (int i = 0; i < msgArray.length(); ++i) {
        JSONObject resultObject = msgArray.getJSONObject(i);
        results.add(new Result(resultObject));
      }
    }

    this.jobUrls = new ArrayList<>();
    if (paragraphJson.has("runtimeInfos")) {
      JSONObject runtimeInfosJson = paragraphJson.getJSONObject("runtimeInfos");
      if (runtimeInfosJson.has("jobUrl")) {
        JSONObject jobUrlJson = runtimeInfosJson.getJSONObject("jobUrl");
        if (jobUrlJson.has("values")) {
          JSONArray valuesArray = jobUrlJson.getJSONArray("values");
          for (int i=0;i< valuesArray.length(); ++i) {
            JSONObject object = valuesArray.getJSONObject(i);
            if (object.has("jobUrl")) {
              jobUrls.add(object.getString("jobUrl"));
            }
          }
        }
      }
    }
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public Status getStatus() {
    return status;
  }

  public int getProgress() {
    return progress;
  }

  public List<Result> getResults() {
    return results;
  }

  public List<String> getJobUrls() {
    return jobUrls;
  }

  /**
   *
   * @return
   */
  public String getMessage() {
    StringBuilder builder = new StringBuilder();
    if (results != null) {
      for (Result result : results) {
        builder.append(result.getData() + "\n");
      }
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return "ParagraphResult{" +
            "paragraphId='" + paragraphId + '\'' +
            ", status=" + status +
            ", results=" + StringUtils.join(results, ", ") +
            ", progress=" + progress +
            '}';
  }

}
