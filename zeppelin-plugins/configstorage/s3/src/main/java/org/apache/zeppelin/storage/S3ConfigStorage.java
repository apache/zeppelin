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
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CryptoConfiguration;
import com.amazonaws.services.s3.model.EncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.KMSEncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * Stores Zeppelin mutable configuration JSON files in S3.
 */
public class S3ConfigStorage extends ConfigStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(S3ConfigStorage.class);

  private static final String S3 = "s3";
  private static final String S3A = "s3a";
  private static final String INTERPRETER_SETTING_FILE = "interpreter.json";
  private static final String NOTEBOOK_AUTHORIZATION_FILE = "notebook-authorization.json";
  private static final String CREDENTIALS_FILE = "credentials.json";

  private final AmazonS3 s3client;
  private final String bucketName;
  private final String configPrefix;
  private final boolean useServerSideEncryption;
  private final CannedAccessControlList objectCannedAcl;

  public S3ConfigStorage(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);
    S3Location configLocation = resolveConfigLocation(zConf);
    this.bucketName = configLocation.bucketName;
    this.configPrefix = configLocation.keyPrefix;
    this.useServerSideEncryption = zConf.isS3ServerSideEncryption();
    if (StringUtils.isNotBlank(zConf.getConfigS3CannedAcl())) {
      this.objectCannedAcl = CannedAccessControlList.valueOf(zConf.getConfigS3CannedAcl());
    } else {
      this.objectCannedAcl = null;
    }
    this.s3client = createS3Client(zConf);
    LOGGER.info("Using S3 prefix s3://{}/{} to store Zeppelin Config", bucketName, configPrefix);
  }

  @Override
  public void save(InterpreterInfoSaving settingInfos) throws IOException {
    saveJson("Interpreter Settings", objectKey(INTERPRETER_SETTING_FILE), settingInfos.toJson());
  }

  @Override
  public InterpreterInfoSaving loadInterpreterSettings() throws IOException {
    String json = loadJson("Interpreter Settings", objectKey(INTERPRETER_SETTING_FILE));
    return json == null ? null : buildInterpreterInfoSaving(json);
  }

  @Override
  public void save(NotebookAuthorizationInfoSaving authorizationInfoSaving) throws IOException {
    saveJson("notebook authorization", objectKey(NOTEBOOK_AUTHORIZATION_FILE),
        authorizationInfoSaving.toJson());
  }

  @Override
  public NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException {
    String json = loadJson("notebook authorization", objectKey(NOTEBOOK_AUTHORIZATION_FILE));
    return json == null ? null : NotebookAuthorizationInfoSaving.fromJson(json);
  }

  @Override
  public String loadCredentials() throws IOException {
    return loadJson("Credentials", objectKey(CREDENTIALS_FILE));
  }

  @Override
  public void saveCredentials(String credentials) throws IOException {
    saveJson("Credentials", objectKey(CREDENTIALS_FILE), credentials);
  }

  public void close() {
    if (s3client != null) {
      s3client.shutdown();
    }
  }

  private String loadJson(String storageName, String key) throws IOException {
    try {
      if (!s3client.doesObjectExist(bucketName, key)) {
        LOGGER.warn("{} file s3://{}/{} is not existed", storageName, bucketName, key);
        return null;
      }
      LOGGER.info("Load {} from S3: s3://{}/{}", storageName, bucketName, key);
      S3Object s3Object = s3client.getObject(new GetObjectRequest(bucketName, key));
      try (InputStream input = s3Object.getObjectContent()) {
        return IOUtils.toString(input, zConf.getString(ConfVars.ZEPPELIN_ENCODING));
      }
    } catch (AmazonClientException e) {
      throw new IOException("Fail to load " + storageName + " from S3", e);
    }
  }

  private void saveJson(String storageName, String key, String json) throws IOException {
    try {
      LOGGER.info("Save {} to S3: s3://{}/{}", storageName, bucketName, key);
      byte[] bytes = json.getBytes(Charset.forName(zConf.getString(ConfVars.ZEPPELIN_ENCODING)));
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(bytes.length);
      metadata.setContentType("application/json");
      if (useServerSideEncryption) {
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
      }
      PutObjectRequest putRequest = new PutObjectRequest(bucketName, key,
          new ByteArrayInputStream(bytes), metadata);
      if (objectCannedAcl != null) {
        putRequest.withCannedAcl(objectCannedAcl);
      }
      s3client.putObject(putRequest);
    } catch (AmazonClientException e) {
      throw new IOException("Fail to save " + storageName + " to S3", e);
    }
  }

  private String objectKey(String fileName) {
    return StringUtils.isBlank(configPrefix) ? fileName : configPrefix + "/" + fileName;
  }

  private static S3Location resolveConfigLocation(ZeppelinConfiguration zConf) throws IOException {
    String configuredDir = firstNonBlank(
        zConf.getConfigS3Dir(),
        zConf.getS3User() + "/config");

    try {
      URI uri = new URI(configuredDir);
      String scheme = uri.getScheme();
      if (StringUtils.isBlank(scheme)) {
        return new S3Location(zConf.getS3BucketName(), normalizePrefix(configuredDir));
      }
      if (!S3.equalsIgnoreCase(scheme) && !S3A.equalsIgnoreCase(scheme)) {
        throw new IOException("Unsupported S3 config storage URI scheme: " + scheme);
      }
      if (StringUtils.isBlank(uri.getHost())) {
        throw new IOException("S3 config storage URI must include a bucket: " + configuredDir);
      }
      return new S3Location(uri.getHost(), normalizePrefix(uri.getPath()));
    } catch (URISyntaxException e) {
      throw new IOException("Invalid S3 config storage location: " + configuredDir, e);
    }
  }

  private static String firstNonBlank(String first, String second) {
    if (StringUtils.isNotBlank(first)) {
      return first;
    }
    return second;
  }

  private static String normalizePrefix(String keyPrefix) {
    if (StringUtils.isBlank(keyPrefix)) {
      return "";
    }
    String normalized = keyPrefix.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static AmazonS3 createS3Client(ZeppelinConfiguration zConf) throws IOException {
    AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    CryptoConfiguration cryptoConf = new CryptoConfiguration();
    String keyRegion = zConf.getS3KMSKeyRegion();

    if (StringUtils.isNotBlank(keyRegion)) {
      cryptoConf.setAwsKmsRegion(Region.getRegion(Regions.fromName(keyRegion)));
    }

    ClientConfiguration clientConf = createClientConfiguration(zConf);
    AmazonS3 client;
    String kmsKeyID = zConf.getS3KMSKeyID();
    if (StringUtils.isNotBlank(kmsKeyID)) {
      KMSEncryptionMaterialsProvider emp = new KMSEncryptionMaterialsProvider(kmsKeyID);
      client = new AmazonS3EncryptionClient(credentialsProvider, emp, clientConf, cryptoConf);
    } else if (StringUtils.isNotBlank(zConf.getS3EncryptionMaterialsProviderClass())) {
      EncryptionMaterialsProvider emp = createCustomProvider(zConf);
      client = new AmazonS3EncryptionClient(credentialsProvider, emp, clientConf, cryptoConf);
    } else {
      client = new AmazonS3Client(credentialsProvider, clientConf);
    }
    client.setS3ClientOptions(S3ClientOptions.builder()
        .setPathStyleAccess(zConf.isS3PathStyleAccess()).build());
    client.setEndpoint(zConf.getS3Endpoint());
    return client;
  }

  private static ClientConfiguration createClientConfiguration(ZeppelinConfiguration zConf) {
    ClientConfiguration config = new ClientConfigurationFactory().getConfig();
    String s3SignerOverride = zConf.getS3SignerOverride();
    if (StringUtils.isNotBlank(s3SignerOverride)) {
      config.setSignerOverride(s3SignerOverride);
    }
    return config;
  }

  private static EncryptionMaterialsProvider createCustomProvider(ZeppelinConfiguration zConf)
      throws IOException {
    String empClassname = zConf.getS3EncryptionMaterialsProviderClass();
    try {
      Object empInstance = Class.forName(empClassname).getDeclaredConstructor().newInstance();
      if (empInstance instanceof EncryptionMaterialsProvider) {
        return (EncryptionMaterialsProvider) empInstance;
      }
      throw new IOException("Class " + empClassname + " does not implement "
          + EncryptionMaterialsProvider.class.getName());
    } catch (ReflectiveOperationException e) {
      throw new IOException("Unable to instantiate encryption materials provider class "
          + empClassname + ": " + e, e);
    }
  }

  private static class S3Location {
    private final String bucketName;
    private final String keyPrefix;

    private S3Location(String bucketName, String keyPrefix) {
      this.bucketName = bucketName;
      this.keyPrefix = keyPrefix;
    }
  }
}
