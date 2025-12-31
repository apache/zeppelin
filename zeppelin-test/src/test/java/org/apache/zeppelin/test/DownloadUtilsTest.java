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

package org.apache.zeppelin.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@Disabled("Takes a long time and depends on external factors.")
class DownloadUtilsTest {

  @Test
  void downloadHadoop() {
    String hadoopHome = DownloadUtils.downloadHadoop("3.4.0");
    Path hadoopHomePath = Paths.get(hadoopHome);
    assertTrue(hadoopHomePath.toFile().exists());
    assertTrue(hadoopHomePath.toFile().isDirectory());
  }

  @Test
  void downloadSpark() {
    String sparkHome = DownloadUtils.downloadSpark();
    Path sparkHomePath = Paths.get(sparkHome);
    assertTrue(sparkHomePath.toFile().exists());
    assertTrue(sparkHomePath.toFile().isDirectory());
  }

  @Test
  void downloadSparkWithScala() {
    String sparkHome = DownloadUtils.downloadSpark(DownloadUtils.DEFAULT_SPARK_VERSION, DownloadUtils.DEFAULT_SPARK_HADOOP_VERSION, "2.13");
    Path sparkHomePath = Paths.get(sparkHome);
    assertTrue(sparkHomePath.toFile().exists());
    assertTrue(sparkHomePath.toFile().isDirectory());
  }

  @Test
  void downloadFlink() {
    String sparkHome = DownloadUtils.downloadFlink("1.16.3", "2.12");
    Path sparkHomePath = Paths.get(sparkHome);
    assertTrue(sparkHomePath.toFile().exists());
    assertTrue(sparkHomePath.toFile().isDirectory());
  }

  @Test
  void downloadLivy() {
    String sparkHome = DownloadUtils.downloadLivy("0.7.1-incubating");
    Path sparkHomePath = Paths.get(sparkHome);
    assertTrue(sparkHomePath.toFile().exists());
    assertTrue(sparkHomePath.toFile().isDirectory());
  }

  @Test
  void downloadLivy080() {
    String sparkHome = DownloadUtils.downloadLivy("0.8.0-incubating", "2.12");
    Path sparkHomePath = Paths.get(sparkHome);
    assertTrue(sparkHomePath.toFile().exists());
    assertTrue(sparkHomePath.toFile().isDirectory());
  }
  
  @Test
  void downloadHBase() {
    String hbaseHome = DownloadUtils.downloadHBase("2.14.8");
    Path hbaseHomePath = Paths.get(hbaseHome);
    assertTrue(hbaseHomePath.toFile().exists());
    assertTrue(hbaseHomePath.toFile().isDirectory());
  }
  
}
