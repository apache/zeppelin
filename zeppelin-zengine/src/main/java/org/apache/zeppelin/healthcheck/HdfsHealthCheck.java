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
package org.apache.zeppelin.healthcheck;

import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.notebook.FileSystemStorage;

import com.codahale.metrics.health.HealthCheck;

public class HdfsHealthCheck extends HealthCheck {
  private final FileSystemStorage fs;
  private final Path path;

  /**
   *
   * @param fs used file system
   * @param path checked path, which should always be present
   */
  public HdfsHealthCheck(FileSystemStorage fs, Path path) {
    this.fs = fs;
    this.path= path;
  }
  @Override
  protected Result check() throws Exception {
    try {
      if (fs.exists(path)) {
        return Result.healthy("Filesystem okay");
      }
    } catch (IOException e) {
      return Result.unhealthy("Filesystem unhealthy", e);
    }
    return Result.unhealthy("Filesystem unhealthy");
  }
}
