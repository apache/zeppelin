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

package org.apache.zeppelin.notebook;

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.util.Date;
import java.util.Map;

/**
 * Subset of the paragraph that filters data by user
 */
public class ParagraphSubset {
  String title;
  String text;
  AuthenticationInfo authenticationInfo;
  Date dateUpdated;
  Map<String, Object> config; // paragraph configs like isOpen, colWidth, etc
  GUI settings;

  String jobName;
  String id;
  Object result;
  Date dateCreated;
  Date dateStarted;
  Date dateFinished;
  Job.Status status;

  ParagraphSubset() {}

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ParagraphSubset{");
    sb.append("jobName=").append(jobName);
    sb.append("\nid=").append(id);
    sb.append("\nresult=").append(result);
    sb.append("\nresult class=").append(result != null ? result.getClass() : "null");
    sb.append("\n}");
    return sb.toString();
  }

}
