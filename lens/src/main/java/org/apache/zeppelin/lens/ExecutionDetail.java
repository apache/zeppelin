/**
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
package org.apache.zeppelin.lens;

import org.apache.lens.client.LensClient;
import org.springframework.shell.core.JLineShell;
/**
 * Pojo tracking query execution details
 * Used to cancel the query
 */
public class ExecutionDetail {
  private String queryHandle;
  private LensClient lensClient;
  private JLineShell shell;
  ExecutionDetail(String qh, LensClient lensClient, JLineShell shell) {
    this.queryHandle = qh;
    this.lensClient = lensClient;
    this.shell = shell;
  }
  public JLineShell getShell() {
    return shell;
  }
  public String getQueryHandle() {
    return queryHandle;
  }
  public LensClient getLensClient() {
    return lensClient;
  }
}
