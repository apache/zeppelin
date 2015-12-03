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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Backend for storing Notebooks on S3
 */
public class S3NotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(S3NotebookRepo.class);

  // Use a credential provider chain so that instance profiles can be utilized
  // on an EC2 instance. The order of locations where credentials are searched
  // is documented here
  //
  //    http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/
  //        auth/DefaultAWSCredentialsProviderChain.html
  //
  // In summary, the order is:
  //
  //  1. Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
  //  2. Java System Properties - aws.accessKeyId and aws.secretKey
  //  3. Credential profiles file at the default location (~/.aws/credentials)
  //       shared by all AWS SDKs and the AWS CLI
  //  4. Instance profile credentials delivered through the Amazon EC2 metadata service
  private AmazonS3 s3client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());
  private static String bucketName = "";
  private String user = "";

  private ZeppelinConfiguration conf;

  public S3NotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    user = conf.getUser();
    bucketName = conf.getBucketName();
  }

  @Override
  public List<NoteInfo> list() throws IOException {
    List<NoteInfo> infos = new LinkedList<NoteInfo>();
    NoteInfo info = null;
    try {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
          .withBucketName(bucketName)
          .withPrefix(user + "/" + "notebook");
      ObjectListing objectListing;
      do {
        objectListing = s3client.listObjects(listObjectsRequest);

        for (S3ObjectSummary objectSummary :
          objectListing.getObjectSummaries()) {
          if (objectSummary.getKey().contains("note.json")) {
            try {
              info = getNoteInfo(objectSummary.getKey());
              if (info != null) {
                infos.add(info);
              }
            } catch (IOException e) {
              LOG.error("Can't read note ", e);
            }
          }
        }
        listObjectsRequest.setMarker(objectListing.getNextMarker());
      } while (objectListing.isTruncated());
    } catch (AmazonServiceException ase) {

    } catch (AmazonClientException ace) {
      LOG.info("Caught an AmazonClientException, " +
          "which means the client encountered " +
          "an internal error while trying to communicate" +
          " with S3, " +
          "such as not being able to access the network.");
      LOG.info("Error Message: " + ace.getMessage());
    }
    return infos;
  }

  private Note getNote(String key) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();

    S3Object s3object = s3client.getObject(new GetObjectRequest(
        bucketName, key));

    InputStream ins = s3object.getObjectContent();
    String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    ins.close();
    Note note = gson.fromJson(json, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }
    }
    return note;
  }

  private NoteInfo getNoteInfo(String key) throws IOException {
    Note note = getNote(key);
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId) throws IOException {
    return getNote(user + "/" + "notebook" + "/" + noteId + "/" + "note.json");
  }

  @Override
  public void save(Note note) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    String json = gson.toJson(note);
    String key = user + "/" + "notebook" + "/" + note.id() + "/" + "note.json";

    File file = File.createTempFile("note", "json");
    file.deleteOnExit();
    Writer writer = new OutputStreamWriter(new FileOutputStream(file));

    writer.write(json);
    writer.close();
    s3client.putObject(new PutObjectRequest(bucketName, key, file));
  }

  @Override
  public void remove(String noteId) throws IOException {
    String key = user + "/" + "notebook" + "/" + noteId;
    final ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
        .withBucketName(bucketName).withPrefix(key);

    ObjectListing objects = s3client.listObjects(listObjectsRequest);
    do {
      for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
        s3client.deleteObject(bucketName, objectSummary.getKey());
      }
      objects = s3client.listNextBatchOfObjects(objects);
    } while (objects.isTruncated());
  }

  @Override
  public void close() {
    //no-op
  }
}
