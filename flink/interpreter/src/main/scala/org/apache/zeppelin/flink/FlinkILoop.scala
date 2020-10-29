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

package org.apache.zeppelin.flink

import java.io.{BufferedReader, File}

import org.apache.flink.configuration.Configuration

import scala.tools.nsc.interpreter._


class FlinkILoop(
    override val flinkConfig: Configuration,
    override val externalJars: Option[Array[String]],
    in0: Option[BufferedReader],
    out0: JPrintWriter) extends org.apache.flink.api.scala.FlinkILoop(flinkConfig, externalJars, in0, out0) {

  override def writeFilesToDisk(): File = {
    // create tmpDirBase again in case it is deleted by system, because it is in the system temp folder.
    val field = getClass.getSuperclass.getDeclaredField("tmpDirBase")
    field.setAccessible(true)
    val tmpDir = field.get(this).asInstanceOf[File]
    if (!tmpDir.exists()) {
      tmpDir.mkdir()
    }
    super.writeFilesToDisk()
  }
}

