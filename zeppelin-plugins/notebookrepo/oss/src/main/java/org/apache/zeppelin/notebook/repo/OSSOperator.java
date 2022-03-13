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

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OSSOperator {
  private OSS ossClient;

  public OSSOperator(String endpoint, String accessKeyId, String accessKeySecret) {
    this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
  }


  public void createBucket(String bucketName) {
    ossClient.createBucket(bucketName);
  }


  public void deleteBucket(String bucketName) {
    ossClient.deleteBucket(bucketName);
  }

  public boolean doesObjectExist(String bucketName, String key) throws IOException {
    return ossClient.doesObjectExist(bucketName, key);
  }


  public String getTextObject(String bucketName, String key) throws IOException {
    if (!doesObjectExist(bucketName, key)) {
      throw new IOException("Note or its revision not found");
    }
    OSSObject ossObject = ossClient.getObject(bucketName, key);
    InputStream in = null;
    try {
      in = ossObject.getObjectContent();
      return IOUtils.toString(in, StandardCharsets.UTF_8);
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }


  public void putTextObject(String bucketName, String key, InputStream inputStream) {
    PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream);
    ossClient.putObject(putObjectRequest);
  }


  public void moveObject(String bucketName, String sourceKey, String destKey) throws IOException {
    if (!doesObjectExist(bucketName, sourceKey)) {
      throw new IOException("Note or its revision not found");
    }
    CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName,
            sourceKey, bucketName, destKey);
    ossClient.copyObject(copyObjectRequest);
    ossClient.deleteObject(bucketName, sourceKey);
  }

  public void moveDir(String bucketName, String sourceDir, String destDir) throws IOException {
    List<String> objectKeys = listDirObjects(bucketName, sourceDir);
    for (String key : objectKeys) {
      moveObject(bucketName, key, destDir + key.substring(sourceDir.length()));
    }
  }


  public void deleteDir(String bucketName, String dirname) {
    List<String> keys = listDirObjects(bucketName, dirname);
    deleteFiles(bucketName, keys);
  }

  public void deleteFile(String bucketName, String objectKey) throws IOException {
    deleteFiles(bucketName, Arrays.asList(objectKey));
  }

  public void deleteFiles(String bucketName, List<String> objectKeys) {
    if (objectKeys != null && objectKeys.size() > 0) {
      DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(objectKeys);
      ossClient.deleteObjects(deleteObjectsRequest);
    }
  }


  public List<String> listDirObjects(String bucketName, String dirname) {
    String nextMarker = null;
    ObjectListing objectListing = null;
    List<String> keys = new ArrayList<>();
    do {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName)
              .withPrefix(dirname)
              .withMarker(nextMarker);
      objectListing = ossClient.listObjects(listObjectsRequest);
      if (!objectListing.getObjectSummaries().isEmpty()) {
        for (OSSObjectSummary s : objectListing.getObjectSummaries()) {
          keys.add(s.getKey());
        }
      }

      nextMarker = objectListing.getNextMarker();
    } while (objectListing.isTruncated());
    return keys;
  }

  public void shutdown() {
    ossClient.shutdown();
  }


}
