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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.model.EncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.KMSEncryptionMaterialsProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
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
  private final AmazonS3 s3client;
  private final String bucketName;
  private final String user;
  private final ZeppelinConfiguration conf;

  public S3NotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    bucketName = conf.getBucketName();
    user = conf.getUser();

    // always use the default provider chain
    AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();

    // see if we should be encrypting data in S3
    String kmsKeyID = conf.getS3KMSKeyID();
    if (kmsKeyID != null) {
      // use the AWS KMS to encrypt data
      KMSEncryptionMaterialsProvider emp = new KMSEncryptionMaterialsProvider(kmsKeyID);
      this.s3client = new AmazonS3EncryptionClient(credentialsProvider, emp);
    }
    else if (conf.getS3EncryptionMaterialsProviderClass() != null) {
      // use a custom encryption materials provider class
      EncryptionMaterialsProvider emp = createCustomProvider(conf);
      this.s3client = new AmazonS3EncryptionClient(credentialsProvider, emp);
    }
    else {
      // regular S3
      this.s3client = new AmazonS3Client(credentialsProvider);
    }

    // set S3 endpoint to use
    s3client.setEndpoint(conf.getEndpoint());
  }

  /**
   * Create an instance of a custom encryption materials provider class
   * which supplies encryption keys to use when reading/writing data in S3.
   */
  private EncryptionMaterialsProvider createCustomProvider(ZeppelinConfiguration conf)
      throws IOException {
    // use a custom encryption materials provider class
    String empClassname = conf.getS3EncryptionMaterialsProviderClass();
    EncryptionMaterialsProvider emp;
    try {
      Object empInstance = Class.forName(empClassname).newInstance();
      if (empInstance instanceof EncryptionMaterialsProvider) {
        emp = (EncryptionMaterialsProvider) empInstance;
      }
      else {
        throw new IOException("Class " + empClassname + " does not implement "
                + EncryptionMaterialsProvider.class.getName());
      }
    }
    catch (Exception e) {
      throw new IOException("Unable to instantiate encryption materials provider class "
              + empClassname + ": " + e, e);
    }

    return emp;
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    List<NoteInfo> infos = new LinkedList<>();
    NoteInfo info;
    try {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
              .withBucketName(bucketName)
              .withPrefix(user + "/" + "notebook");
      ObjectListing objectListing;
      do {
        objectListing = s3client.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
          if (objectSummary.getKey().endsWith("note.json")) {
            info = getNoteInfo(objectSummary.getKey());
            if (info != null) {
              infos.add(info);
            }
          }
        }
        listObjectsRequest.setMarker(objectListing.getNextMarker());
      } while (objectListing.isTruncated());
    } catch (AmazonClientException ace) {
      throw new IOException("Unable to list objects in S3: " + ace, ace);
    }
    return infos;
  }

  private Note getNote(String key) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.registerTypeAdapter(Date.class, new NotebookImportDeserializer())
        .create();

    S3Object s3object;
    try {
      s3object = s3client.getObject(new GetObjectRequest(bucketName, key));
    }
    catch (AmazonClientException ace) {
      throw new IOException("Unable to retrieve object from S3: " + ace, ace);
    }

    Note note;
    try (InputStream ins = s3object.getObjectContent()) {
      String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
      note = gson.fromJson(json, Note.class);
    }

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
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    return getNote(user + "/" + "notebook" + "/" + noteId + "/" + "note.json");
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    String json = gson.toJson(note);
    String key = user + "/" + "notebook" + "/" + note.id() + "/" + "note.json";

    File file = File.createTempFile("note", "json");
    try {
      Writer writer = new OutputStreamWriter(new FileOutputStream(file));
      writer.write(json);
      writer.close();
      s3client.putObject(new PutObjectRequest(bucketName, key, file));
    }
    catch (AmazonClientException ace) {
      throw new IOException("Unable to store note in S3: " + ace, ace);
    }
    finally {
      FileUtils.deleteQuietly(file);
    }
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    String key = user + "/" + "notebook" + "/" + noteId;
    final ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
        .withBucketName(bucketName).withPrefix(key);

    try {
      ObjectListing objects = s3client.listObjects(listObjectsRequest);
      do {
        for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
          s3client.deleteObject(bucketName, objectSummary.getKey());
        }
        objects = s3client.listNextBatchOfObjects(objects);
      } while (objects.isTruncated());
    }
    catch (AmazonClientException ace) {
      throw new IOException("Unable to remove note in S3: " + ace, ace);
    }
  }

  @Override
  public void close() {
    //no-op
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    // Auto-generated method stub
    return null;
  }

  @Override
  public Note get(String noteId, Revision rev, AuthenticationInfo subject) throws IOException {
    // Auto-generated method stub
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    // Auto-generated method stub
    return null;
  }
}
