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

package org.apache.zeppelin.notebook.repo;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.zeppelin.notebook.repo.storage.RemoteStorageOperator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MockStorageOperator implements RemoteStorageOperator {

  private String mockRootFolder;

  public MockStorageOperator() throws IOException {
    Path tempDirectory = Files.createTempDirectory("zeppelin_mock_storage_dir_");
    mockRootFolder = tempDirectory.toString() + "/";
  }

  @Override
  public void createBucket(String bucketName) throws IOException {
    FileUtils.forceMkdir(new File(mockRootFolder + bucketName));
  }

  @Override
  public void deleteBucket(String bucketName) throws IOException {
    FileUtils.deleteDirectory(new File(mockRootFolder + bucketName));
  }

  @Override
  public boolean doesObjectExist(String bucketName, String key) throws IOException {
    File file = new File(mockRootFolder + bucketName + "/" + key);
    return file.exists() && !file.isDirectory();
  }

  @Override
  public String getTextObject(String bucketName, String key) throws IOException {
    if (!doesObjectExist(bucketName, key)) {
      throw new IOException("Note or its revision not found");
    }
    return FileUtils.readFileToString(new File(mockRootFolder + bucketName + "/" + key), "UTF-8");
  }

  @Override
  public void putTextObject(String bucketName, String key, InputStream inputStream) throws IOException {
    File destination = new File(mockRootFolder + bucketName + "/" + key);
    destination.getParentFile().mkdirs();
    FileUtils.copyInputStreamToFile(inputStream, destination);
  }

  @Override
  public void moveObject(String bucketName, String sourceKey, String destKey) throws IOException {
    FileUtils.moveFile(new File(mockRootFolder + bucketName + "/" + sourceKey),
            new File(mockRootFolder + bucketName + "/" + destKey));
  }

  @Override
  public void moveDir(String bucketName, String sourceDir, String destDir) throws IOException {
    List<String> objectKeys = listDirObjects(bucketName, sourceDir);
    for (String key : objectKeys) {
      moveObject(bucketName, key, destDir + key.substring(sourceDir.length()));
    }
  }

  @Override
  public void deleteDir(String bucketName, String dirname) throws IOException {
    List<String> keys = listDirObjects(bucketName, dirname);
    deleteFiles(bucketName, keys);
  }

  @Override
  public void deleteFile(String bucketName, String objectKey) throws IOException {
    FileUtils.forceDelete(new File(mockRootFolder + bucketName + "/" + objectKey));
  }

  @Override
  public void deleteFiles(String bucketName, List<String> objectKeys) throws IOException {
    if (objectKeys != null && objectKeys.size() > 0) {
      for (String objectKey : objectKeys) {
        deleteFile(bucketName, objectKey);
      }
    }
  }

  @Override
  public List<String> listDirObjects(String bucketName, String dirname) {
    File directory = new File(mockRootFolder + bucketName + "/" + dirname);
    if (!directory.isDirectory()) {
      return new ArrayList<>();
    }
    Collection<File> files = FileUtils.listFiles(directory, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    return files.stream().map(file -> file.getPath().substring((mockRootFolder + bucketName + "/").length())).collect(Collectors.toList());
  }

  @Override
  public void shutdown() {

  }

}
