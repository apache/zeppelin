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

package org.apache.zeppelin.rest.message;

import org.apache.zeppelin.notebook.Paragraph;

public class ParagraphJobStatus {
  private String id;
  private String status;
  private String started;
  private String finished;
  private String progress;

  public ParagraphJobStatus(Paragraph p) {
    this.id = p.getId();
    this.status = p.getStatus().toString();
    if (p.getDateStarted() != null) {
      this.started = p.getDateStarted().toString();
    }
    if (p.getDateFinished() != null) {
      this.finished = p.getDateFinished().toString();
    }
    if (p.getStatus().isRunning()) {
      this.progress = String.valueOf(p.progress());
    } else if (p.isTerminated()){
      this.progress = String.valueOf(100);
    } else {
      this.progress = String.valueOf(0);
    }
  }

  public String getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public String getStarted() {
    return started;
  }

  public String getFinished() {
    return finished;
  }

  public String getProgress() {
    return progress;
  }
}
