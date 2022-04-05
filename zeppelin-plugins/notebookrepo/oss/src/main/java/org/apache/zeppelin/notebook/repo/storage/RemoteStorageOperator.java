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

package org.apache.zeppelin.notebook.repo.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface RemoteStorageOperator {
  void createBucket(String bucketName) throws IOException;

  void deleteBucket(String bucketName) throws IOException;

  boolean doesObjectExist(String bucketName, String key) throws IOException;

  String getTextObject(String bucketName, String key) throws IOException;

  void putTextObject(String bucketName, String key, InputStream inputStream) throws IOException;

  void moveObject(String bucketName, String sourceKey, String destKey) throws IOException;

  void moveDir(String bucketName, String sourceDir, String destDir) throws IOException;

  void deleteDir(String bucketName, String dirname) throws IOException;

  void deleteFile(String bucketName, String objectKey) throws IOException;

  void deleteFiles(String bucketName, List<String> objectKeys) throws IOException;

  List<String> listDirObjects(String bucketName, String dirname);

  void shutdown();
}
