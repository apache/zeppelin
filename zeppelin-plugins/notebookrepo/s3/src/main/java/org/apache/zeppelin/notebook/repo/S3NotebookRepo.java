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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.ClientConfigurationFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CryptoConfiguration;
import com.amazonaws.services.s3.model.EncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.KMSEncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * Backend for storing Notebooks on S3
 */
public class S3NotebookRepo implements NotebookRepo {
  private static final Logger LOGGER = LoggerFactory.getLogger(S3NotebookRepo.class);

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
  private AmazonS3 s3client;
  private String bucketName;
  private String user;
  private boolean useServerSideEncryption;
  private CannedAccessControlList objectCannedAcl;
  private ZeppelinConfiguration conf;
  private String rootFolder;

  public S3NotebookRepo() {

  }

  public void init(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    bucketName = conf.getS3BucketName();
    user = conf.getS3User();
    rootFolder = user + "/notebook";
    useServerSideEncryption = conf.isS3ServerSideEncryption();
    if (StringUtils.isNotBlank(conf.getS3CannedAcl())) {
        objectCannedAcl = CannedAccessControlList.valueOf(conf.getS3CannedAcl());
    }

    // always use the default provider chain
    AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    CryptoConfiguration cryptoConf = new CryptoConfiguration();
    String keyRegion = conf.getS3KMSKeyRegion();

    if (StringUtils.isNotBlank(keyRegion)) {
      cryptoConf.setAwsKmsRegion(Region.getRegion(Regions.fromName(keyRegion)));
    }

    ClientConfiguration cliConf = createClientConfiguration();
    
    // see if we should be encrypting data in S3
    String kmsKeyID = conf.getS3KMSKeyID();
    if (kmsKeyID != null) {
      // use the AWS KMS to encrypt data
      KMSEncryptionMaterialsProvider emp = new KMSEncryptionMaterialsProvider(kmsKeyID);
      this.s3client = new AmazonS3EncryptionClient(credentialsProvider, emp, cliConf, cryptoConf);
    }
    else if (conf.getS3EncryptionMaterialsProviderClass() != null) {
      // use a custom encryption materials provider class
      EncryptionMaterialsProvider emp = createCustomProvider(conf);
      this.s3client = new AmazonS3EncryptionClient(credentialsProvider, emp, cliConf, cryptoConf);
    }
    else {
      // regular S3
      this.s3client = new AmazonS3Client(credentialsProvider, cliConf);
    }
    s3client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(conf.isS3PathStyleAccess()).build());

    // set S3 endpoint to use
    s3client.setEndpoint(conf.getS3Endpoint());
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

  /**
   * Create AWS client configuration and return it.
   * @return AWS client configuration
   */
  private ClientConfiguration createClientConfiguration() {
    ClientConfigurationFactory configFactory = new ClientConfigurationFactory();
    ClientConfiguration config = configFactory.getConfig();

    String s3SignerOverride = conf.getS3SignerOverride();
    if (StringUtils.isNotBlank(s3SignerOverride)) {
      config.setSignerOverride(s3SignerOverride);
    }

    return config;
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    Map<String, NoteInfo> notesInfo = new HashMap<>();
    try {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
              .withBucketName(bucketName)
              .withPrefix(user + "/" + "notebook");
      ObjectListing objectListing;
      do {
        objectListing = s3client.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
          if (objectSummary.getKey().endsWith(".zpln")) {
            try {
              NoteInfo info = getNoteInfo(objectSummary.getKey());
              notesInfo.put(info.getId(), info);
            } catch (IOException e) {
              LOGGER.warn(e.getMessage());
            }
          }
        }
        listObjectsRequest.setMarker(objectListing.getNextMarker());
      } while (objectListing.isTruncated());
    } catch (AmazonClientException ace) {
      throw new IOException("Fail to list objects in S3", ace);
    }
    return notesInfo;
  }

  private NoteInfo getNoteInfo(String key) throws IOException {
    return new NoteInfo(getNoteId(key), getNotePath(rootFolder, key));
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    S3Object s3object;
    try {
      s3object = s3client.getObject(new GetObjectRequest(bucketName,
          rootFolder + "/" + buildNoteFileName(noteId, notePath)));
    }
    catch (AmazonClientException ace) {
      throw new IOException("Fail to get note: " + notePath + " from S3", ace);
    }
    try (InputStream ins = s3object.getObjectContent()) {
      String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
      return Note.fromJson(noteId, json);
    }
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    String json = note.toJson();
    String key = rootFolder + "/" + buildNoteFileName(note);
    File file = File.createTempFile("note", "zpln");
    try {
      Writer writer = new OutputStreamWriter(new FileOutputStream(file));
      writer.write(json);
      writer.close();
      PutObjectRequest putRequest = new PutObjectRequest(bucketName, key, file);
      if (useServerSideEncryption) {
        // Request server-side encryption.
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        putRequest.setMetadata(objectMetadata);
      }
      if (objectCannedAcl != null) {
          putRequest.withCannedAcl(objectCannedAcl);
      }
      s3client.putObject(putRequest);
    }
    catch (AmazonClientException ace) {
      throw new IOException("Fail to store note: " + note.getPath() + " in S3", ace);
    }
    finally {
      FileUtils.deleteQuietly(file);
    }
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    String key = rootFolder + "/" + buildNoteFileName(noteId, notePath);
    String newKey = rootFolder + "/" + buildNoteFileName(noteId, newNotePath);
    s3client.copyObject(bucketName, key, bucketName, newKey);
    s3client.deleteObject(bucketName, key);
  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) throws IOException {
    try {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
              .withBucketName(bucketName)
              .withPrefix(rootFolder + folderPath + "/");
      ObjectListing objectListing;
      do {
        objectListing = s3client.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
          if (objectSummary.getKey().endsWith(".zpln")) {
            String noteId = getNoteId(objectSummary.getKey());
            String notePath = getNotePath(rootFolder, objectSummary.getKey());
            String newNotePath = newFolderPath + notePath.substring(folderPath.length());
            move(noteId, notePath, newNotePath, subject);
          }
        }
        listObjectsRequest.setMarker(objectListing.getNextMarker());
      } while (objectListing.isTruncated());
    } catch (AmazonClientException ace) {
      throw new IOException("Fail to move folder: " + folderPath + " to " + newFolderPath  + " in S3" , ace);
    }
  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject)
      throws IOException {
    try {
      s3client.deleteObject(bucketName, rootFolder + "/" + buildNoteFileName(noteId, notePath));
    } catch (AmazonClientException ace) {
      throw new IOException("Fail to remove note: " + notePath + " from S3", ace);
    }
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    final ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
            .withBucketName(bucketName).withPrefix(rootFolder + folderPath + "/");
    try {
      ObjectListing objects = s3client.listObjects(listObjectsRequest);
      do {
        for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
          s3client.deleteObject(bucketName, objectSummary.getKey());
        }
        objects = s3client.listNextBatchOfObjects(objects);
      } while (objects.isTruncated());
    } catch (AmazonClientException ace) {
      throw new IOException("Unable to remove folder " + folderPath  + " in S3", ace);
    }
  }

  @Override
  public void close() {
    if (s3client != null) {
      s3client.shutdown();
    }
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
