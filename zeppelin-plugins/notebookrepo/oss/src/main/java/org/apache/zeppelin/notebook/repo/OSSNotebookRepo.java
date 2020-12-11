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
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.PutObjectRequest;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * NotebookRepo for Aliyun OSS (https://cn.aliyun.com/product/oss)
 */
public class OSSNotebookRepo implements NotebookRepo {
  private static final Logger LOGGER = LoggerFactory.getLogger(OSSNotebookRepo.class);

  private OSS ossClient;
  private String bucketName;
  private String rootFolder;

  public OSSNotebookRepo() {
  }

  @Override
  public void init(ZeppelinConfiguration conf) throws IOException {
    String endpoint = conf.getOSSEndpoint();
    bucketName = conf.getOSSBucketName();
    rootFolder = conf.getNotebookDir();
    // rootFolder is part of OSS key which doesn't start with '/'
    if (rootFolder.startsWith("/")) {
      rootFolder = rootFolder.substring(1);
    }
    String accessKeyId = conf.getOSSAccessKeyId();
    String accessKeySecret = conf.getOSSAccessKeySecret();
    this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    Map<String, NoteInfo> notesInfo = new HashMap<>();
    final int maxKeys = 200;
    String nextMarker = null;
    ObjectListing objectListing = null;
    do {
      objectListing = ossClient.listObjects(
              new ListObjectsRequest(bucketName)
                      .withPrefix(rootFolder + "/")
                      .withMarker(nextMarker)
                      .withMaxKeys(maxKeys));
      List<OSSObjectSummary> sums = objectListing.getObjectSummaries();
      for (OSSObjectSummary s : sums) {
        if (s.getKey().endsWith(".zpln")) {
          try {
            String noteId = getNoteId(s.getKey());
            String notePath = getNotePath(rootFolder, s.getKey());
            notesInfo.put(noteId, new NoteInfo(noteId, notePath));
          } catch (IOException e) {
            LOGGER.warn(e.getMessage());
          }
        } else {
          LOGGER.debug("Skip invalid note file: {}", s.getKey());
        }
      }
      nextMarker = objectListing.getNextMarker();
    } while (objectListing.isTruncated());

    return notesInfo;
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    OSSObject ossObject = ossClient.getObject(bucketName,
            rootFolder + "/" + buildNoteFileName(noteId, notePath));
    InputStream in = null;
    try {
      in = ossObject.getObjectContent();
      return Note.fromJson(IOUtils.toString(in, StandardCharsets.UTF_8));
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    String content = note.toJson();
    PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName,
            rootFolder + "/" + buildNoteFileName(note.getId(), note.getPath()),
            new ByteArrayInputStream(content.getBytes()));
    ossClient.putObject(putObjectRequest);
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    String sourceKey = rootFolder + "/" + buildNoteFileName(noteId, notePath);
    String destKey = rootFolder + "/" + buildNoteFileName(noteId, newNotePath);
    CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName,
            sourceKey, bucketName, destKey);
    ossClient.copyObject(copyObjectRequest);
    ossClient.deleteObject(bucketName, sourceKey);
  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {
    final int maxKeys = 200;
    String nextMarker = null;
    ObjectListing objectListing = null;
    do {
      objectListing = ossClient.listObjects(
              new ListObjectsRequest(bucketName)
                      .withPrefix(rootFolder + folderPath + "/")
                      .withMarker(nextMarker)
                      .withMaxKeys(maxKeys));
      List<OSSObjectSummary> sums = objectListing.getObjectSummaries();
      for (OSSObjectSummary s : sums) {
        if (s.getKey().endsWith(".zpln")) {
          try {
            String noteId = getNoteId(s.getKey());
            String notePath = getNotePath(rootFolder, s.getKey());
            String newNotePath = newFolderPath + notePath.substring(folderPath.length());
            move(noteId, notePath, newNotePath, subject);
          } catch (IOException e) {
            LOGGER.warn(e.getMessage());
          }
        } else {
          LOGGER.debug("Skip invalid note file: {}", s.getKey());
        }
      }
      nextMarker = objectListing.getNextMarker();
    } while (objectListing.isTruncated());

  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject)
      throws IOException {
    ossClient.deleteObject(bucketName, rootFolder + "/" + buildNoteFileName(noteId, notePath));
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) {
    String nextMarker = null;
    ObjectListing objectListing = null;
    do {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName)
              .withPrefix(rootFolder + folderPath + "/")
              .withMarker(nextMarker);
      objectListing = ossClient.listObjects(listObjectsRequest);
      if (!objectListing.getObjectSummaries().isEmpty()) {
        List<String> keys = new ArrayList<>();
        for (OSSObjectSummary s : objectListing.getObjectSummaries()) {
          keys.add(s.getKey());
        }
        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(keys);
        ossClient.deleteObjects(deleteObjectsRequest);
      }

      nextMarker = objectListing.getNextMarker();
    } while (objectListing.isTruncated());
  }

  @Override
  public void close() {
    ossClient.shutdown();
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOGGER.warn("Method not implemented");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOGGER.warn("Method not implemented");
  }

}
