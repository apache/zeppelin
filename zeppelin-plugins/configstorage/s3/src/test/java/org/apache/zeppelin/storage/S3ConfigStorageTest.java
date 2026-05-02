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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.gaul.s3proxy.junit.S3ProxyExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3ConfigStorageTest {

  private static final String CONFIG_PREFIX = "config-root";

  @RegisterExtension
  static S3ProxyExtension s3Proxy = S3ProxyExtension.builder()
      .withCredentials("access", "secret")
      .build();

  private AmazonS3 s3Client;
  private S3ConfigStorage configStorage;
  private String bucket;
  private String previousAccessKeyId;
  private String previousSecretKey;

  @BeforeEach
  void setUp() throws IOException {
    previousAccessKeyId = System.getProperty("aws.accessKeyId");
    previousSecretKey = System.getProperty("aws.secretKey");
    System.setProperty("aws.accessKeyId", s3Proxy.getAccessKey());
    System.setProperty("aws.secretKey", s3Proxy.getSecretKey());

    s3Client = AmazonS3ClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(s3Proxy.getAccessKey(), s3Proxy.getSecretKey())))
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
            s3Proxy.getUri().toString(), Regions.US_EAST_1.getName()))
        .build();
    bucket = "test-bucket-" + UUID.randomUUID();
    s3Client.createBucket(bucket);

    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT.getVarName(),
        s3Proxy.getUri().toString());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_BUCKET.getVarName(),
        bucket);
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_S3_DIR.getVarName(),
        CONFIG_PREFIX);
    configStorage = new S3ConfigStorage(zConf);
  }

  @AfterEach
  void tearDown() {
    if (configStorage != null) {
      configStorage.close();
    }
    if (s3Client != null) {
      s3Client.shutdown();
    }
    restoreSystemProperty("aws.accessKeyId", previousAccessKeyId);
    restoreSystemProperty("aws.secretKey", previousSecretKey);
  }

  @Test
  void testSaveAndLoadConfigFiles() throws IOException {
    assertNull(configStorage.loadInterpreterSettings());
    assertNull(configStorage.loadNotebookAuthorization());
    assertNull(configStorage.loadCredentials());

    InterpreterInfoSaving interpreterInfoSaving = new InterpreterInfoSaving();
    configStorage.save(interpreterInfoSaving);

    NotebookAuthorizationInfoSaving authorizationInfoSaving =
        new NotebookAuthorizationInfoSaving(new ConcurrentHashMap<>());
    configStorage.save(authorizationInfoSaving);

    String credentials = "{\n  \"credentialsMap\": {}\n}";
    configStorage.saveCredentials(credentials);

    InterpreterInfoSaving loadedInterpreterInfo = configStorage.loadInterpreterSettings();
    assertNotNull(loadedInterpreterInfo);
    assertTrue(loadedInterpreterInfo.interpreterSettings.isEmpty());

    NotebookAuthorizationInfoSaving loadedAuthorization = configStorage.loadNotebookAuthorization();
    assertNotNull(loadedAuthorization);
    assertTrue(loadedAuthorization.getAuthInfo().isEmpty());

    assertEquals(credentials, configStorage.loadCredentials());
    assertTrue(s3Client.doesObjectExist(bucket, CONFIG_PREFIX + "/interpreter.json"));
    assertTrue(s3Client.doesObjectExist(bucket, CONFIG_PREFIX + "/notebook-authorization.json"));
    assertTrue(s3Client.doesObjectExist(bucket, CONFIG_PREFIX + "/credentials.json"));
  }

  @Test
  void testS3UriCanOverrideBucketAndPrefix() throws IOException {
    String uriBucket = "uri-bucket-" + UUID.randomUUID();
    s3Client.createBucket(uriBucket);

    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT.getVarName(),
        s3Proxy.getUri().toString());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_S3_DIR.getVarName(),
        "s3://" + uriBucket + "/custom/config/");

    S3ConfigStorage uriStorage = new S3ConfigStorage(zConf);
    try {
      uriStorage.saveCredentials("{}");
      assertTrue(s3Client.doesObjectExist(uriBucket, "custom/config/credentials.json"));
    } finally {
      uriStorage.close();
    }
  }

  @Test
  void testDefaultPrefixUsesS3UserConfigDirectory() throws IOException {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT.getVarName(),
        s3Proxy.getUri().toString());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_BUCKET.getVarName(),
        bucket);
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_USER.getVarName(),
        "zeppelin-user");

    S3ConfigStorage defaultPrefixStorage = new S3ConfigStorage(zConf);
    try {
      defaultPrefixStorage.saveCredentials("{}");
      assertTrue(s3Client.doesObjectExist(bucket,
          "zeppelin-user/config/credentials.json"));
    } finally {
      defaultPrefixStorage.close();
    }
  }

  private void restoreSystemProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }
}
