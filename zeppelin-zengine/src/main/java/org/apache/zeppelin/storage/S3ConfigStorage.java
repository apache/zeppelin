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

package org.apache.zeppelin.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.ClientConfigurationFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Storing config in aws s3 file system
 */
public class S3ConfigStorage extends ConfigStorage {


  private static Logger LOGGER = LoggerFactory.getLogger(S3ConfigStorage.class);

  private AmazonS3 s3client;
  private String bucketName;
  private String user;
  private String rootFolder;
  private String interpreterSettingPath;
  private String authorizationPath;



  public S3ConfigStorage(ZeppelinConfiguration zConf) {
    super(zConf);
    bucketName = zConf.getS3BucketName();
    user = zConf.getS3User();
    rootFolder = user + "/conf";
    this.interpreterSettingPath = rootFolder + "/interpreter.json";
    this.authorizationPath = rootFolder + "/notebook-authorization.json";

    // always use the default provider chain
    AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();

    ClientConfigurationFactory configFactory = new ClientConfigurationFactory();
    ClientConfiguration cliConf = configFactory.getConfig();

    // regular S3
    this.s3client = new AmazonS3Client(credentialsProvider, cliConf);

    // set S3 endpoint to use
    s3client.setEndpoint(zConf.getS3Endpoint());
  }

  @Override
  public void save(InterpreterInfoSaving settingInfos) throws IOException {
    LOGGER.info("Save Interpreter Setting to s3://{}/{}", this.bucketName, this.interpreterSettingPath);
    saveToS3(settingInfos.toJson(), interpreterSettingPath,"zeppelin-interpreter");
  }

  @Override
  public InterpreterInfoSaving loadInterpreterSettings() throws IOException {
    LOGGER.info("Load Interpreter Setting from s3 Path: " + interpreterSettingPath);
    String json = readFromS3(interpreterSettingPath);
    return buildInterpreterInfoSaving(json);
  }

  @Override
  public void save(NotebookAuthorizationInfoSaving authorizationInfoSaving) throws IOException {
    LOGGER.info("Save notebook authorization to s3://{}/{} ",this.bucketName,this.authorizationPath);
    saveToS3(authorizationInfoSaving.toJson(), authorizationPath,"notebook-authorization");
  }

  @Override
  public NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException {
    LOGGER.info("Load notebook authorization from s3 Path: " + interpreterSettingPath);
    String json = readFromS3(interpreterSettingPath);
    return NotebookAuthorizationInfoSaving.fromJson(json);
  }

  @Override
  public String loadCredentials() throws IOException {
    return null;
  }

  @Override
  public void saveCredentials(String credentials) throws IOException {

  }

  @VisibleForTesting
  void saveToS3(String content, String s3Path,String tempFileName) throws IOException {
    File file = File.createTempFile(tempFileName, "zpln");
    try {
      Writer writer = new OutputStreamWriter(new FileOutputStream(file));
      writer.write(content);
      writer.close();
      PutObjectRequest putRequest = new PutObjectRequest(bucketName, s3Path, file);
      s3client.putObject(putRequest);
    }
    catch (AmazonClientException ace) {
      throw new IOException("Fail to store " + tempFileName + ": " + s3Path + " in S3", ace);
    }
    finally {
      FileUtils.deleteQuietly(file);
    }
  }

  @VisibleForTesting
  String readFromS3( String filePath) throws IOException {
    S3Object s3object;
    try {
      s3object = s3client.getObject(new GetObjectRequest(bucketName,
              filePath));
    }
    catch (AmazonClientException ace) {
      throw new IOException("Fail to get file: " + filePath + " from S3", ace);
    }
    try (InputStream ins = s3object.getObjectContent()) {
      return IOUtils.toString(ins, zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
    }
  }

}
