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

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.amazonaws.AmazonClientException;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Storing config in Aliyun OSS file system
 */
public class OSSConfigStorage extends ConfigStorage {


  private static Logger LOGGER = LoggerFactory.getLogger(OSSConfigStorage.class);



  private OSS ossClient;
  private String bucketName;
  private String interpreterSettingPath;
  private String authorizationPath;



  public OSSConfigStorage(ZeppelinConfiguration zConf) {
    super(zConf);
    String endpoint = zConf.getOSSEndpoint();
    bucketName = zConf.getOSSBucketName();
    String rootFolder = zConf.getNotebookDir();
    if (rootFolder.startsWith("/")) {
      rootFolder = rootFolder.substring(1);
    }
    this.interpreterSettingPath = rootFolder + "/interpreter.json";
    this.authorizationPath = rootFolder + "/notebook-authorization.json";
    String accessKeyId = zConf.getOSSAccessKeyId();
    String accessKeySecret = zConf.getOSSAccessKeySecret();
    this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
  }

  @Override
  public void save(InterpreterInfoSaving settingInfos) throws IOException {
    LOGGER.info("Save Interpreter Setting to oss://{}/{}", this.bucketName, this.interpreterSettingPath);
    saveToOSS(settingInfos.toJson(), interpreterSettingPath);
  }

  @Override
  public InterpreterInfoSaving loadInterpreterSettings() throws IOException {
    LOGGER.info("Load Interpreter Setting from oss Path: " + interpreterSettingPath);
    String json = readFromOSS(interpreterSettingPath);
    return buildInterpreterInfoSaving(json);
  }

  @Override
  public void save(NotebookAuthorizationInfoSaving authorizationInfoSaving) throws IOException {
    LOGGER.info("Save notebook authorization to oss://{}/{} ",this.bucketName,this.authorizationPath);
    saveToOSS(authorizationInfoSaving.toJson(), authorizationPath);
  }

  @Override
  public NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException {
    LOGGER.info("Load notebook authorization from oss Path: " + interpreterSettingPath);
    String json = readFromOSS(interpreterSettingPath);
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
  void saveToOSS(String content, String ossPath) throws IOException {
    try {
      PutObjectRequest putObjectRequest = new com.aliyun.oss.model.PutObjectRequest(bucketName,
              ossPath, new ByteArrayInputStream(content.getBytes()));
      ossClient.putObject(putObjectRequest);
    }
    catch (AmazonClientException ace) {
      throw new IOException("Fail to store " + ossPath + " in OSS", ace);
    }

  }

  @VisibleForTesting
  String readFromOSS( String filePath) throws IOException {

    OSSObject ossObject;
    try {
      ossObject = ossClient.getObject(bucketName, filePath);
    }
    catch (Exception e){
      throw new IOException("Fail to get file: " + filePath + " from OSS", e);
    }

    try (InputStream in = ossObject.getObjectContent()){
      return IOUtils.toString(in,zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
    }
  }

}
