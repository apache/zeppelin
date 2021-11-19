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

package org.apache.zeppelin.conf;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.configuration2.EnvironmentConfiguration;
import org.apache.commons.configuration2.SystemConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.ClasspathLocationStrategy;
import org.apache.commons.configuration2.io.CombinedLocationStrategy;
import org.apache.commons.configuration2.io.FileLocationStrategy;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.lifecycle.NullLifecycleManager;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin configuration.
 *
 * Sources descending by priority:
 *   - environment variables
 *   - system properties
 *   - configuration file
 */
public class ZeppelinConfiguration {

  private static final String ZEPPELIN_SITE_XML = "zeppelin-site.xml";
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinConfiguration.class);

  private Boolean anonymousAllowed;

  private static ZeppelinConfiguration conf;

  private static final EnvironmentConfiguration envConfig = new EnvironmentConfiguration();
  private static final SystemConfiguration sysConfig = new SystemConfiguration();

  private final Map<String, String> properties = new HashMap<>();

  public enum RUN_MODE {
    LOCAL,
    K8S,
    DOCKER
  }

  // private constructor, so that it is singleton.
  private ZeppelinConfiguration(@Nullable String filename) {
     try {
      loadXMLConfig(filename);
    } catch (ConfigurationException e) {
      LOGGER.warn("Failed to load XML configuration, proceeding with a default,for a stacktrace activate the debug log");
      LOGGER.debug("Failed to load XML configuration", e);
    }
  }

  private void loadXMLConfig(@Nullable String filename) throws ConfigurationException {
    if (StringUtils.isBlank(filename)) {
      filename = ZEPPELIN_SITE_XML;
    }
    List<FileLocationStrategy> subs = Arrays.asList(
      new ZeppelinLocationStrategy(),
      new ClasspathLocationStrategy());
    FileLocationStrategy strategy = new CombinedLocationStrategy(subs);
    Parameters params = new Parameters();
    FileBasedConfigurationBuilder<XMLConfiguration> xmlbuilder =
      new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
      .configure(params.xml()
        .setLocationStrategy(strategy)
        .setFileName(filename)
        .setBasePath(File.separator + "conf" + File.separator));
    XMLConfiguration xmlConfig = xmlbuilder.getConfiguration();
    List<ImmutableNode> nodes = xmlConfig.getNodeModel().getRootNode().getChildren();
    if (nodes != null && !nodes.isEmpty()) {
      for (ImmutableNode p : nodes) {
        String name = String.valueOf(p.getChildren("name").get(0).getValue());
        String value = String.valueOf(p.getChildren("value").get(0).getValue());
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(value)) {
          setProperty(name, value);
        }
      }
    }
  }

  public static ZeppelinConfiguration create() {
    if (conf != null) {
      return conf;
    }
    return ZeppelinConfiguration.create(null);
  }
  /**
   * Load from via filename.
   */
  public static synchronized ZeppelinConfiguration create(@Nullable String filename) {
    if (conf != null) {
      return conf;
    }

    conf = new ZeppelinConfiguration(filename);


    LOGGER.info("Server Host: {}", conf.getServerAddress());
    if (conf.useSsl()) {
      LOGGER.info("Server SSL Port: {}", conf.getServerSslPort());
    } else {
      LOGGER.info("Server Port: {}", conf.getServerPort());
    }
    LOGGER.info("Context Path: {}", conf.getServerContextPath());
    LOGGER.info("Zeppelin Version: {}", Util.getVersion());

    return conf;
  }

  public static void reset() {
    conf = null;
  }

  public void setProperty(String name, String value) {
    if (StringUtils.isNoneBlank(name, value)) {
      this.properties.put(name, value);
    }
  }

  private String getStringValue(String name, String d) {
    String value = this.properties.get(name);
    if (value != null) {
      return value;
    }
    return d;
  }

  private int getIntValue(String name, int d) {
    String value = this.properties.get(name);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        LOGGER.warn("Can not parse the property {} with the value \"{}\" to an int value", name, value, e);
      }
    }
    return d;
  }

  private long getLongValue(String name, long d) {
    String value = this.properties.get(name);
    if (value != null) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        LOGGER.warn("Can not parse the property {} with the value \"{}\" to a long value", name, value, e);
      }
    }
    return d;
  }

  private float getFloatValue(String name, float d) {
    String value = this.properties.get(name);
    if (value != null) {
      try {
        return Float.parseFloat(value);
      } catch (NumberFormatException e) {
        LOGGER.warn("Can not parse the property {} with the value \"{}\" to a float value", name, value, e);
      }
    }
    return d;
  }

  private boolean getBooleanValue(String name, boolean d) {
    String value = this.properties.get(name);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }
    return d;
  }

  public String getString(ConfVars c) {
    return getString(c.name(), c.getVarName(), c.getStringValue());
  }

  public String getString(String envName, String propertyName, String defaultValue) {
    if (envConfig.containsKey(envName)) {
      return envConfig.getString(envName);
    }
    if (sysConfig.containsKey(propertyName)) {
      return sysConfig.getString(propertyName);
    }
    return getStringValue(propertyName, defaultValue);
  }

  public int getInt(ConfVars c) {
    return getInt(c.name(), c.getVarName(), c.getIntValue());
  }

  public int getInt(String envName, String propertyName, int defaultValue) {
    if (envConfig.containsKey(envName)) {
      return envConfig.getInt(envName);
    }
    if (sysConfig.containsKey(propertyName)) {
      return sysConfig.getInt(propertyName);
    }
    return getIntValue(propertyName, defaultValue);
  }

  public long getLong(ConfVars c) {
    return getLong(c.name(), c.getVarName(), c.getLongValue());
  }

  public long getLong(String envName, String propertyName, long defaultValue) {
    if (envConfig.containsKey(envName)) {
      return envConfig.getLong(envName);
    }
    if (sysConfig.containsKey(propertyName)) {
      return sysConfig.getLong(propertyName);
    }
    return getLongValue(propertyName, defaultValue);
  }

  public float getFloat(ConfVars c) {
    return getFloat(c.name(), c.getVarName(), c.getFloatValue());
  }

  public float getFloat(String envName, String propertyName, float defaultValue) {
    if (envConfig.containsKey(envName)) {
      return envConfig.getFloat(envName);
    }
    if (sysConfig.containsKey(propertyName)) {
      return sysConfig.getFloat(propertyName);
    }
    return getFloatValue(propertyName, defaultValue);
  }

  public boolean getBoolean(ConfVars c) {
    return getBoolean(c.name(), c.getVarName(), c.getBooleanValue());
  }

  public boolean getBoolean(String envName, String propertyName, boolean defaultValue) {
    if (envConfig.containsKey(envName)) {
      return envConfig.getBoolean(envName);
    }
    if (sysConfig.containsKey(propertyName)) {
      return sysConfig.getBoolean(propertyName);
    }
    return getBooleanValue(propertyName, defaultValue);
  }

  public String getZeppelinHome() {
    return getString(ConfVars.ZEPPELIN_HOME);
  }

  public boolean useSsl() {
    return getBoolean(ConfVars.ZEPPELIN_SSL);
  }

  public int getServerSslPort() {
    return getInt(ConfVars.ZEPPELIN_SSL_PORT);
  }

  public boolean useClientAuth() {
    return getBoolean(ConfVars.ZEPPELIN_SSL_CLIENT_AUTH);
  }

  public String getServerAddress() {
    return getString(ConfVars.ZEPPELIN_ADDR);
  }

  @VisibleForTesting
  public void setServerPort(int port) {
    properties.put(ConfVars.ZEPPELIN_PORT.getVarName(), String.valueOf(port));
  }

  public int getServerPort() {
    return getInt(ConfVars.ZEPPELIN_PORT);
  }

  public String getServerContextPath() {
    return getString(ConfVars.ZEPPELIN_SERVER_CONTEXT_PATH);
  }

  public String getKeyStorePath() {
    String path = getString(ConfVars.ZEPPELIN_SSL_KEYSTORE_PATH);
    if (path != null && path.startsWith("/") || isWindowsPath(path)) {
      return path;
    } else {
      return getAbsoluteDir(
          String.format("%s/%s",
              getConfDir(),
              path));
    }
  }

  public String getKeyStoreType() {
    return getString(ConfVars.ZEPPELIN_SSL_KEYSTORE_TYPE);
  }

  public String getKeyStorePassword() {
    return getString(ConfVars.ZEPPELIN_SSL_KEYSTORE_PASSWORD);
  }

  public String getKeyManagerPassword() {
    String password = getString(ConfVars.ZEPPELIN_SSL_KEY_MANAGER_PASSWORD);
    if (password == null) {
      return getKeyStorePassword();
    } else {
      return password;
    }
  }

  public String getTrustStorePath() {
    String path = getString(ConfVars.ZEPPELIN_SSL_TRUSTSTORE_PATH);
    if (path == null) {
      path = getKeyStorePath();
    }
    if (path != null && path.startsWith("/") || isWindowsPath(path)) {
      return path;
    } else {
      return getAbsoluteDir(
          String.format("%s/%s",
              getConfDir(),
              path));
    }
  }

  public String getTrustStoreType() {
    String type = getString(ConfVars.ZEPPELIN_SSL_TRUSTSTORE_TYPE);
    if (type == null) {
      return getKeyStoreType();
    } else {
      return type;
    }
  }

  public String getTrustStorePassword() {
    String password = getString(ConfVars.ZEPPELIN_SSL_TRUSTSTORE_PASSWORD);
    if (password == null) {
      return getKeyStorePassword();
    } else {
      return password;
    }
  }

  public String getPemKeyFile() {
      return getString(ConfVars.ZEPPELIN_SSL_PEM_KEY);
  }

  public String getPemKeyPassword() {
      return getString(ConfVars.ZEPPELIN_SSL_PEM_KEY_PASSWORD);
  }

  public String getPemCertFile() {
      return getString(ConfVars.ZEPPELIN_SSL_PEM_CERT);
  }

  public String getPemCAFile() {
      return getString(ConfVars.ZEPPELIN_SSL_PEM_CA);
  }

  public boolean isJMXEnabled() {
    return getBoolean(ConfVars.ZEPPELIN_JMX_ENABLE);
  }

  public int getJMXPort() {
    return getInt(ConfVars.ZEPPELIN_JMX_PORT);
  }

  public String getNotebookDir() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_NOTEBOOK_DIR);
  }

  public String getNotebookRunId() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_RUN_ID);
  }

  public String getNotebookRunRev() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_RUN_REV);
  }

  public String getNotebookRunServiceContext() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_RUN_SERVICE_CONTEXT);
  }

  public boolean getNotebookRunAutoShutdown() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_RUN_AUTOSHUTDOWN);
  }

  public String getPluginsDir() {
    return getAbsoluteDir(getString(ConfVars.ZEPPELIN_PLUGINS_DIR));
  }

  public String getRecoveryDir() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_RECOVERY_DIR);
  }

  public String getNotebookStorageClass() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE);
  }

  public String getRecoveryStorageClass() {
    return getString(ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS);
  }

  public boolean isRecoveryEnabled() {
    return !getString(ConfVars.ZEPPELIN_RECOVERY_STORAGE_CLASS).equals(
        "org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage");
  }

  public String getGCSStorageDir() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR);
  }

  public String getS3User() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_USER);
  }

  public String getS3BucketName() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_BUCKET);
  }

  public String getS3Endpoint() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT);
  }

  public String getS3Timeout() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_TIMEOUT);
  }

  public String getS3KMSKeyID() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID);
  }

  public String getS3KMSKeyRegion() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_KMS_KEY_REGION);
  }

  public String getS3EncryptionMaterialsProviderClass() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_EMP);
  }

  public boolean isS3ServerSideEncryption() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_S3_SSE);
  }

  public String getS3SignerOverride() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_SIGNEROVERRIDE);
  }

  public boolean isS3PathStyleAccess() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_S3_PATH_STYLE_ACCESS);
  }

  public String getS3CannedAcl() {
      return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_CANNED_ACL);
  }

  public String getOSSBucketName() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_OSS_BUCKET);
  }

  public String getOSSEndpoint() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_OSS_ENDPOINT);
  }

  public String getOSSAccessKeyId() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_OSS_ACCESSKEYID);
  }

  public String getOSSAccessKeySecret() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_OSS_ACCESSKEYSECRET);
  }

  public String getMongoUri() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_URI);
  }

  public String getMongoDatabase() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_DATABASE);
  }

  public String getMongoCollection() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_COLLECTION);
  }

  public String getMongoFolder() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_FOLDER);
  }
  public boolean getMongoAutoimport() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT);
  }

  public String getInterpreterListPath() {
    return getAbsoluteDir(String.format("%s/interpreter-list", getConfDir()));
  }

  public String getInterpreterDir() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_INTERPRETER_DIR);
  }

  public String getInterpreterJson() {
    return getString(ConfVars.ZEPPELIN_INTERPRETER_JSON);
  }

  public String getInterpreterSettingPath(boolean absolute) {
    return getConfigFSDir(absolute) + "/interpreter.json";
  }

  public String getHeliumConfPath() {
    return getAbsoluteDir(String.format("%s/helium.json", getConfDir()));
  }

  public String getHeliumRegistry() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_HELIUM_REGISTRY);
  }

  public String getHeliumNodeInstallerUrl() {
    return getString(ConfVars.ZEPPELIN_HELIUM_NODE_INSTALLER_URL);
  }

  public String getHeliumNpmInstallerUrl() {
    return getString(ConfVars.ZEPPELIN_HELIUM_NPM_INSTALLER_URL);
  }

  public String getHeliumYarnInstallerUrl() {
    return getString(ConfVars.ZEPPELIN_HELIUM_YARNPKG_INSTALLER_URL);
  }

  public String getNotebookAuthorizationPath(boolean absolute) {
    return getConfigFSDir(absolute) + "/notebook-authorization.json";
  }

  public boolean credentialsPersist() {
    return getBoolean(ConfVars.ZEPPELIN_CREDENTIALS_PERSIST);
  }

  public String getCredentialsEncryptKey() {
    return getString(ConfVars.ZEPPELIN_CREDENTIALS_ENCRYPT_KEY);
  }

  public String getCredentialsPath(boolean absolute) {
    return getConfigFSDir(absolute) + "/credentials.json";
  }

  public String getShiroPath() {
    String shiroPath = getAbsoluteDir(String.format("%s/shiro.ini", getConfDir()));
    return new File(shiroPath).exists() ? shiroPath : StringUtils.EMPTY;
  }

  public String getInterpreterRemoteRunnerPath() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER);
  }

  public String getInterpreterLocalRepoPath() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO);
  }

  public String getInterpreterMvnRepoPath() {
    return getString(ConfVars.ZEPPELIN_INTERPRETER_DEP_MVNREPO);
  }

  public String getAbsoluteDir(ConfVars c) {
    return getAbsoluteDir(getString(c));
  }

  public String getAbsoluteDir(String path) {
    if (path != null && (path.startsWith(File.separator) || isWindowsPath(path) || isPathWithScheme(path))) {
      return path;
    } else {
      return getString(ConfVars.ZEPPELIN_HOME) + File.separator + path;
    }
  }

  public String getZeppelinServerRPCPortRange() {
    return getString(ConfVars.ZEPPELIN_SERVER_RPC_PORTRANGE);
  }

  public String[] getNoteFileExcludedFields() {
    return StringUtils.split(getString(ConfVars.ZEPPELIN_NOTE_FILE_EXCLUDE_FIELDS), (","));
  }

  public String getInterpreterPortRange() {
    return getString(ConfVars.ZEPPELIN_INTERPRETER_RPC_PORTRANGE);
  }

  public boolean isWindowsPath(String path){
    return path.matches("^[A-Za-z]:\\\\.*");
  }

  public boolean isPathWithScheme(String path){
      try {
        return StringUtils.isNotBlank(new URI(path).getScheme());
    } catch (URISyntaxException e) {
        return false;
    }
  }

  public boolean isAnonymousAllowed() {
    if (anonymousAllowed == null) {
      anonymousAllowed = this.getShiroPath().equals(StringUtils.EMPTY);
    }
    return anonymousAllowed;
  }

  public boolean isJobManagerEnabled() {
    return getBoolean(ConfVars.ZEPPELIN_JOBMANAGER_ENABLE);
  }

  public boolean isUsernameForceLowerCase() {
    return getBoolean(ConfVars.ZEPPELIN_USERNAME_FORCE_LOWERCASE);
  }

  public boolean isNotebookPublic() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC);
  }

  public String getConfDir() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_CONF_DIR);
  }

  public String getConfigFSDir(boolean absolute) {
    String fsConfigDir = getString(ConfVars.ZEPPELIN_CONFIG_FS_DIR);
    if (StringUtils.isBlank(fsConfigDir)) {
      LOGGER.warn("{} is not specified, fall back to local conf directory {}",
        ConfVars.ZEPPELIN_CONFIG_FS_DIR.varName,  ConfVars.ZEPPELIN_CONF_DIR.varName);
      if (absolute) {
        return getConfDir();
      } else {
        return getString(ConfVars.ZEPPELIN_CONF_DIR);
      }
    }
    if (getString(ConfVars.ZEPPELIN_CONFIG_STORAGE_CLASS)
                .equals("org.apache.zeppelin.storage.LocalConfigStorage")) {
      // only apply getRelativeDir when it is LocalConfigStorage
      return getAbsoluteDir(fsConfigDir);
    } else {
      return fsConfigDir;
    }
  }

  public List<String> getAllowedOrigins()
  {
    if (getString(ConfVars.ZEPPELIN_ALLOWED_ORIGINS).isEmpty()) {
      return Collections.emptyList();
    }

    return Arrays.asList(getString(ConfVars.ZEPPELIN_ALLOWED_ORIGINS).toLowerCase().split(","));
  }

  public String getWebsocketMaxTextMessageSize() {
    return getString(ConfVars.ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE);
  }

  public String getJettyName() {
    return getString(ConfVars.ZEPPELIN_SERVER_JETTY_NAME);
  }

  public boolean sendJettyName() {
    return getBoolean(ConfVars.ZEPPELIN_SERVER_SEND_JETTY_NAME);
  }

  public Integer getJettyRequestHeaderSize() {
    return getInt(ConfVars.ZEPPELIN_SERVER_JETTY_REQUEST_HEADER_SIZE);
  }

  public boolean isAuthorizationHeaderClear() {
    return getBoolean(ConfVars.ZEPPELIN_SERVER_AUTHORIZATION_HEADER_CLEAR);
  }


  public String getXFrameOptions() {
    return getString(ConfVars.ZEPPELIN_SERVER_XFRAME_OPTIONS);
  }

  public String getXxssProtection() {
    return getString(ConfVars.ZEPPELIN_SERVER_X_XSS_PROTECTION);
  }

  public String getXContentTypeOptions() {
    return getString(ConfVars.ZEPPELIN_SERVER_X_CONTENT_TYPE_OPTIONS);
  }

  public String getStrictTransport() {
    return getString(ConfVars.ZEPPELIN_SERVER_STRICT_TRANSPORT);
  }

  public String getHtmlHeadAddon() {
    return getString(ConfVars.ZEPPELIN_SERVER_HTML_HEAD_ADDON);
  }

  public String getHtmlBodyAddon() {
    return getString(ConfVars.ZEPPELIN_SERVER_HTML_BODY_ADDON);
  }

  public String getLifecycleManagerClass() {
    return getString(ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS);
  }

  public boolean getZeppelinImpersonateSparkProxyUser() {
      return getBoolean(ConfVars.ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER);
  }

  public String getZeppelinNotebookGitURL() {
    return  getString(ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL);
  }

  public String getZeppelinNotebookGitUsername() {
    return  getString(ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_USERNAME);
  }

  public String getZeppelinNotebookGitAccessToken() {
    return  getString(ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_ACCESS_TOKEN);
  }

  public String getZeppelinNotebookGitRemoteOrigin() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_ORIGIN);
  }

  public boolean isZeppelinNotebookCronEnable() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE);
  }

  public String getZeppelinNotebookCronFolders() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS);
  }

  public Boolean isZeppelinNotebookCollaborativeModeEnable() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_COLLABORATIVE_MODE_ENABLE);
  }

  public String getZeppelinProxyUrl() {
    return getString(ConfVars.ZEPPELIN_PROXY_URL);
  }

  public String getZeppelinProxyUser() {
    return getString(ConfVars.ZEPPELIN_PROXY_USER);
  }

  public String getZeppelinProxyPassword() {
    return getString(ConfVars.ZEPPELIN_PROXY_PASSWORD);
  }

  public boolean isIndexRebuild() {
    return getBoolean(ConfVars.ZEPPELIN_SEARCH_INDEX_REBUILD);
  }

  public boolean isZeppelinSearchUseDisk() {
    return getBoolean(ConfVars.ZEPPELIN_SEARCH_USE_DISK);
  }

  public String getZeppelinSearchIndexPath() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_SEARCH_INDEX_PATH);
  }

  public boolean isOnlyYarnCluster() {
    return getBoolean(ConfVars.ZEPPELIN_SPARK_ONLY_YARN_CLUSTER);
  }

  public String getClusterAddress() {
    return getString(ConfVars.ZEPPELIN_CLUSTER_ADDR);
  }

  public void setClusterAddress(String clusterAddr) {
    properties.put(ConfVars.ZEPPELIN_CLUSTER_ADDR.getVarName(), clusterAddr);
  }

  public boolean isClusterMode() {
    return !StringUtils.isEmpty(getString(ConfVars.ZEPPELIN_CLUSTER_ADDR));
  }

  public int getClusterHeartbeatInterval() {
    return getInt(ConfVars.ZEPPELIN_CLUSTER_HEARTBEAT_INTERVAL);
  }

  public int getClusterHeartbeatTimeout() {
    return getInt(ConfVars.ZEPPELIN_CLUSTER_HEARTBEAT_TIMEOUT);
  }

  public RUN_MODE getRunMode() {
    String mode = getString(ConfVars.ZEPPELIN_RUN_MODE);
    if ("auto".equalsIgnoreCase(mode)) { // auto detect
      if (new File("/var/run/secrets/kubernetes.io/serviceaccount/namespace").exists()) {
        return RUN_MODE.K8S;
      } else {
        return RUN_MODE.LOCAL;
      }
    } else {
      return RUN_MODE.valueOf(mode.toUpperCase());
    }
  }

  @VisibleForTesting
  public void setRunMode(RUN_MODE runMode) {
    properties.put(ConfVars.ZEPPELIN_RUN_MODE.getVarName(), runMode.name());
  }

  public boolean getK8sPortForward() {
    return getBoolean(ConfVars.ZEPPELIN_K8S_PORTFORWARD);
  }

  public String getK8sNamepsace() {
    return getString(ConfVars.ZEPPELIN_K8S_NAMESPACE);
  }

  public String getK8sContainerImage() {
    return getString(ConfVars.ZEPPELIN_K8S_CONTAINER_IMAGE);
  }

  public String getK8sSparkContainerImage() {
    return getString(ConfVars.ZEPPELIN_K8S_SPARK_CONTAINER_IMAGE);
  }

  public String getK8sTemplatesDir() {
    return getAbsoluteDir(ConfVars.ZEPPELIN_K8S_TEMPLATE_DIR);
  }

  public String getK8sServiceName() {
    return getString(ConfVars.ZEPPELIN_K8S_SERVICE_NAME);
  }

  public boolean getK8sTimeoutDuringPending() {
    return getBoolean(ConfVars.ZEPPELIN_K8S_TIMEOUT_DURING_PENDING);
  }

  public String getDockerContainerImage() {
    return getString(ConfVars.ZEPPELIN_DOCKER_CONTAINER_IMAGE);
  }

  public boolean isPrometheusMetricEnabled() {
    return getBoolean(ConfVars.ZEPPELIN_METRIC_ENABLE_PROMETHEUS);
  }

  /**
   * This method return the complete configuration map
   * @return
   */
  public Map<String, String> getCompleteConfiguration() {
    Map<String, String> completeConfiguration = new HashMap<>();
    for (ConfVars c : ConfVars.values()) {
      if (getString(c) != null){
        completeConfiguration.put(c.getVarName(), getString(c));
      }
    }
    return completeConfiguration;
  }

  public Map<String, String> dumpConfigurations(Predicate<String> predicate) {
    return getCompleteConfiguration().entrySet().stream()
      .filter(e -> predicate.test(e.getKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public void save(String location) throws ConfigurationException {
    try (FileWriter writer = new FileWriter(location)){
      writer.write("<configuration>\n");
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        writer.write("<property>\n");
        writer.write("<name>" + entry.getKey() + "</name>\n");
        writer.write("<value>" + entry.getValue() + "</value>\n");
        writer.write("</property>\n");
      }
      writer.write("</configuration>");
    } catch (IOException e) {
      throw new ConfigurationException(e);
    }
  }

  /**
   * Wrapper class.
   */
  public enum ConfVars {
    ZEPPELIN_HOME("zeppelin.home", "./"),
    ZEPPELIN_ADDR("zeppelin.server.addr", "127.0.0.1"),
    ZEPPELIN_PORT("zeppelin.server.port", 8080),
    ZEPPELIN_SERVER_CONTEXT_PATH("zeppelin.server.context.path", "/"),
    ZEPPELIN_SSL("zeppelin.ssl", false),
    ZEPPELIN_SSL_PORT("zeppelin.server.ssl.port", 8443),
    ZEPPELIN_SSL_CLIENT_AUTH("zeppelin.ssl.client.auth", false),
    ZEPPELIN_SSL_KEYSTORE_PATH("zeppelin.ssl.keystore.path", "keystore"),
    ZEPPELIN_SSL_KEYSTORE_TYPE("zeppelin.ssl.keystore.type", "JKS"),
    ZEPPELIN_SSL_KEYSTORE_PASSWORD("zeppelin.ssl.keystore.password", ""),
    ZEPPELIN_SSL_KEY_MANAGER_PASSWORD("zeppelin.ssl.key.manager.password", null),
    ZEPPELIN_SSL_PEM_KEY("zeppelin.ssl.pem.key", null),
    ZEPPELIN_SSL_PEM_KEY_PASSWORD("zeppelin.ssl.pem.key.password", ""),
    ZEPPELIN_SSL_PEM_CERT("zeppelin.ssl.pem.cert", null),
    ZEPPELIN_SSL_PEM_CA("zeppelin.ssl.pem.ca", null),
    ZEPPELIN_SSL_TRUSTSTORE_PATH("zeppelin.ssl.truststore.path", null),
    ZEPPELIN_SSL_TRUSTSTORE_TYPE("zeppelin.ssl.truststore.type", null),
    ZEPPELIN_SSL_TRUSTSTORE_PASSWORD("zeppelin.ssl.truststore.password", null),
    ZEPPELIN_WAR("zeppelin.war", "zeppelin-web/dist"),
    ZEPPELIN_ANGULAR_WAR("zeppelin.angular.war", "zeppelin-web-angular/dist"),
    ZEPPELIN_WAR_TEMPDIR("zeppelin.war.tempdir", "webapps"),
    ZEPPELIN_JMX_ENABLE("zeppelin.jmx.enable", false),
    ZEPPELIN_JMX_PORT("zeppelin.jmx.port", 9996),

    ZEPPELIN_INTERPRETER_JSON("zeppelin.interpreter.setting", "interpreter-setting.json"),
    ZEPPELIN_INTERPRETER_DIR("zeppelin.interpreter.dir", "interpreter"),
    ZEPPELIN_INTERPRETER_JUPYTER_KERNELS("zeppelin.interpreter.jupyter.kernels", "python:python,ir:r"),
    ZEPPELIN_INTERPRETER_LOCALREPO("zeppelin.interpreter.localRepo", "local-repo"),
    ZEPPELIN_INTERPRETER_DEP_MVNREPO("zeppelin.interpreter.dep.mvnRepo",
        "https://repo1.maven.org/maven2/"),
    ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT("zeppelin.interpreter.connect.timeout", 60000),
    ZEPPELIN_INTERPRETER_CONNECTION_POOL_SIZE("zeppelin.interpreter.connection.poolsize", 100),
    ZEPPELIN_INTERPRETER_GROUP_DEFAULT("zeppelin.interpreter.group.default", "spark"),
    ZEPPELIN_INTERPRETER_OUTPUT_LIMIT("zeppelin.interpreter.output.limit", 1024 * 100),
    ZEPPELIN_INTERPRETER_INCLUDES("zeppelin.interpreter.include", ""),
    ZEPPELIN_INTERPRETER_EXCLUDES("zeppelin.interpreter.exclude", ""),

    ZEPPELIN_ENCODING("zeppelin.encoding", "UTF-8"),
    ZEPPELIN_NOTEBOOK_DIR("zeppelin.notebook.dir", "notebook"),

    ZEPPELIN_NOTEBOOK_RUN_ID("zeppelin.notebook.run.id", null),   // run particular note id on zeppelin start
    ZEPPELIN_NOTEBOOK_RUN_REV("zeppelin.notebook.run.rev", null), // revision id for ZEPPELIN_NOTEBOOK_RUN_ID.
    ZEPPELIN_NOTEBOOK_RUN_SERVICE_CONTEXT("zeppelin.notebook.run.servicecontext", null), // base64 encoded serialized service context to be used ZEPPELIN_NOTEBOOK_RUN_ID.
    ZEPPELIN_NOTEBOOK_RUN_AUTOSHUTDOWN("zeppelin.notebook.run.autoshutdown", true), // after specified note (ZEPPELIN_NOTEBOOK_RUN_ID) run, shutdown zeppelin server

    ZEPPELIN_NOTEBOOK_RUN_ALL_ISOLATED("zeppelin.notebook.run_all.isolated", false), // whether using isolated mode for RUN_ALL action

    ZEPPELIN_RECOVERY_DIR("zeppelin.recovery.dir", "recovery"),
    ZEPPELIN_RECOVERY_STORAGE_CLASS("zeppelin.recovery.storage.class",
        "org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage"),
    ZEPPELIN_PLUGINS_DIR("zeppelin.plugins.dir", "plugins"),

    // use specified notebook (id) as homescreen
    ZEPPELIN_NOTEBOOK_HOMESCREEN("zeppelin.notebook.homescreen", null),
    // whether homescreen notebook will be hidden from notebook list or not
    ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE("zeppelin.notebook.homescreen.hide", false),
    ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR("zeppelin.notebook.gcs.dir", ""),
    ZEPPELIN_NOTEBOOK_GCS_CREDENTIALS_FILE("zeppelin.notebook.google.credentialsJsonFilePath", null),
    ZEPPELIN_NOTEBOOK_S3_BUCKET("zeppelin.notebook.s3.bucket", "zeppelin"),
    ZEPPELIN_NOTEBOOK_S3_PATH_STYLE_ACCESS("zeppelin.notebook.s3.pathStyleAccess", false),
    ZEPPELIN_NOTEBOOK_S3_ENDPOINT("zeppelin.notebook.s3.endpoint", "s3.amazonaws.com"),
    ZEPPELIN_NOTEBOOK_S3_TIMEOUT("zeppelin.notebook.s3.timeout", "120000"),
    ZEPPELIN_NOTEBOOK_S3_USER("zeppelin.notebook.s3.user", "user"),
    ZEPPELIN_NOTEBOOK_S3_EMP("zeppelin.notebook.s3.encryptionMaterialsProvider", null),
    ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID("zeppelin.notebook.s3.kmsKeyID", null),
    ZEPPELIN_NOTEBOOK_S3_KMS_KEY_REGION("zeppelin.notebook.s3.kmsKeyRegion", null),
    ZEPPELIN_NOTEBOOK_S3_SSE("zeppelin.notebook.s3.sse", false),
    ZEPPELIN_NOTEBOOK_S3_SIGNEROVERRIDE("zeppelin.notebook.s3.signerOverride", null),
    ZEPPELIN_NOTEBOOK_S3_CANNED_ACL("zeppelin.notebook.s3.cannedAcl", null),
    ZEPPELIN_NOTEBOOK_OSS_BUCKET("zeppelin.notebook.oss.bucket", "zeppelin"),
    ZEPPELIN_NOTEBOOK_OSS_ENDPOINT("zeppelin.notebook.oss.endpoint", "http://oss-cn-hangzhou.aliyuncs.com"),
    ZEPPELIN_NOTEBOOK_OSS_ACCESSKEYID("zeppelin.notebook.oss.accesskeyid", null),
    ZEPPELIN_NOTEBOOK_OSS_ACCESSKEYSECRET("zeppelin.notebook.oss.accesskeysecret", null),
    ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING("zeppelin.notebook.azure.connectionString", null),
    ZEPPELIN_NOTEBOOK_AZURE_SHARE("zeppelin.notebook.azure.share", "zeppelin"),
    ZEPPELIN_NOTEBOOK_AZURE_USER("zeppelin.notebook.azure.user", "user"),
    ZEPPELIN_NOTEBOOK_MONGO_DATABASE("zeppelin.notebook.mongo.database", "zeppelin"),
    ZEPPELIN_NOTEBOOK_MONGO_COLLECTION("zeppelin.notebook.mongo.collection", "notes"),
    ZEPPELIN_NOTEBOOK_MONGO_FOLDER("zeppelin.notebook.mongo.folder", "folders"),
    ZEPPELIN_NOTEBOOK_MONGO_URI("zeppelin.notebook.mongo.uri", "mongodb://localhost"),
    ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT("zeppelin.notebook.mongo.autoimport", false),
    ZEPPELIN_NOTEBOOK_STORAGE("zeppelin.notebook.storage",
        "org.apache.zeppelin.notebook.repo.GitNotebookRepo"),
    ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC("zeppelin.notebook.one.way.sync", false),
    // whether by default note is public or private
    ZEPPELIN_NOTEBOOK_PUBLIC("zeppelin.notebook.public", true),
    ZEPPELIN_INTERPRETER_REMOTE_RUNNER("zeppelin.interpreter.remoterunner",
        System.getProperty("os.name")
                .startsWith("Windows") ? "bin/interpreter.cmd" : "bin/interpreter.sh"),
    // Decide when new note is created, interpreter settings will be binded automatically or not.
    ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING("zeppelin.notebook.autoInterpreterBinding", true),
    ZEPPELIN_CONF_DIR("zeppelin.conf.dir", "conf"),
    ZEPPELIN_CONFIG_FS_DIR("zeppelin.config.fs.dir", ""),
    ZEPPELIN_CONFIG_STORAGE_CLASS("zeppelin.config.storage.class",
        "org.apache.zeppelin.storage.LocalConfigStorage"),
    ZEPPELIN_DEP_LOCALREPO("zeppelin.dep.localrepo", "local-repo"),
    ZEPPELIN_HELIUM_REGISTRY("zeppelin.helium.registry", "helium"),
    ZEPPELIN_HELIUM_NODE_INSTALLER_URL("zeppelin.helium.node.installer.url",
            "https://nodejs.org/dist/"),
    ZEPPELIN_HELIUM_NPM_INSTALLER_URL("zeppelin.helium.npm.installer.url",
            "https://registry.npmjs.org/"),
    ZEPPELIN_HELIUM_YARNPKG_INSTALLER_URL("zeppelin.helium.yarnpkg.installer.url",
            "https://github.com/yarnpkg/yarn/releases/download/"),
    // Allows a way to specify a ',' separated list of allowed origins for rest and websockets
    // i.e. http://localhost:8080
    ZEPPELIN_ALLOWED_ORIGINS("zeppelin.server.allowed.origins", "*"),
    ZEPPELIN_USERNAME_FORCE_LOWERCASE("zeppelin.username.force.lowercase", false),
    ZEPPELIN_CREDENTIALS_PERSIST("zeppelin.credentials.persist", true),
    ZEPPELIN_CREDENTIALS_ENCRYPT_KEY("zeppelin.credentials.encryptKey", null),
    ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE("zeppelin.websocket.max.text.message.size", "10240000"),
    ZEPPELIN_WEBSOCKET_PARAGRAPH_STATUS_PROGRESS("zeppelin.websocket.paragraph_status_progress.enable", true),
    ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED("zeppelin.server.default.dir.allowed", false),
    ZEPPELIN_SERVER_XFRAME_OPTIONS("zeppelin.server.xframe.options", "SAMEORIGIN"),
    ZEPPELIN_SERVER_JETTY_NAME("zeppelin.server.jetty.name", " "),
    ZEPPELIN_SERVER_SEND_JETTY_NAME("zeppelin.server.send.jetty.name", true),
    ZEPPELIN_SERVER_JETTY_THREAD_POOL_MAX("zeppelin.server.jetty.thread.pool.max", 400),
    ZEPPELIN_SERVER_JETTY_THREAD_POOL_MIN("zeppelin.server.jetty.thread.pool.min", 8),
    ZEPPELIN_SERVER_JETTY_THREAD_POOL_TIMEOUT("zeppelin.server.jetty.thread.pool.timeout", 30),
    ZEPPELIN_SERVER_JETTY_REQUEST_HEADER_SIZE("zeppelin.server.jetty.request.header.size", 8192),
    ZEPPELIN_SERVER_AUTHORIZATION_HEADER_CLEAR("zeppelin.server.authorization.header.clear", true),
    ZEPPELIN_SERVER_STRICT_TRANSPORT("zeppelin.server.strict.transport", "max-age=631138519"),
    ZEPPELIN_SERVER_X_XSS_PROTECTION("zeppelin.server.xxss.protection", "1; mode=block"),
    ZEPPELIN_SERVER_X_CONTENT_TYPE_OPTIONS("zeppelin.server.xcontent.type.options", "nosniff"),

    ZEPPELIN_SERVER_HTML_HEAD_ADDON("zeppelin.server.html.head.addon", null),
    ZEPPELIN_SERVER_HTML_BODY_ADDON("zeppelin.server.html.body.addon", null),

    ZEPPELIN_SERVER_KERBEROS_KEYTAB("zeppelin.server.kerberos.keytab", ""),
    ZEPPELIN_SERVER_KERBEROS_PRINCIPAL("zeppelin.server.kerberos.principal", ""),

    ZEPPELIN_SERVER_RPC_PORTRANGE("zeppelin.server.rpc.portRange", ":"),
    ZEPPELIN_INTERPRETER_RPC_PORTRANGE("zeppelin.interpreter.rpc.portRange", ":"),

    ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS("zeppelin.interpreter.lifecyclemanager.class",
            NullLifecycleManager.class.getName()),
    ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL(
        "zeppelin.interpreter.lifecyclemanager.timeout.checkinterval", 60000L),
    ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD(
        "zeppelin.interpreter.lifecyclemanager.timeout.threshold", 3600000L),

    ZEPPELIN_INTERPRETER_YARN_MONITOR_INTERVAL_SECS(
            "zeppelin.interpreter.yarn.monitor.interval_secs", 10),

    ZEPPELIN_INTERPRETER_SCHEDULER_POOL_SIZE("zeppelin.scheduler.threadpool.size", 100),

    ZEPPELIN_OWNER_ROLE("zeppelin.notebook.default.owner.username", ""),

    ZEPPELIN_CLUSTER_ADDR("zeppelin.cluster.addr", ""),
    ZEPPELIN_CLUSTER_HEARTBEAT_INTERVAL("zeppelin.cluster.heartbeat.interval", 3000),
    ZEPPELIN_CLUSTER_HEARTBEAT_TIMEOUT("zeppelin.cluster.heartbeat.timeout", 9000),

    ZEPPELIN_RUN_MODE("zeppelin.run.mode", "auto"),              // auto | local | k8s | Docker

    ZEPPELIN_K8S_PORTFORWARD("zeppelin.k8s.portforward", false), // kubectl port-forward incase of Zeppelin is running outside of kuberentes
    ZEPPELIN_K8S_CONTAINER_IMAGE("zeppelin.k8s.container.image", "apache/zeppelin:" + Util.getVersion()),
    ZEPPELIN_K8S_NAMESPACE("zeppelin.k8s.namespace", "default"), // specify a namespace incase of Zeppelin is running outside of kuberentes
    ZEPPELIN_K8S_SPARK_CONTAINER_IMAGE("zeppelin.k8s.spark.container.image", "apache/spark:latest"),
    ZEPPELIN_K8S_TEMPLATE_DIR("zeppelin.k8s.template.dir", "k8s"),
    ZEPPELIN_K8S_SERVICE_NAME("zeppelin.k8s.service.name", "zeppelin-server"),
    ZEPPELIN_K8S_TIMEOUT_DURING_PENDING("zeppelin.k8s.timeout.during.pending", true),

    ZEPPELIN_DOCKER_CONTAINER_IMAGE("zeppelin.docker.container.image", "apache/zeppelin:" + Util.getVersion()),

    ZEPPELIN_METRIC_ENABLE_PROMETHEUS("zeppelin.metric.enable.prometheus", false),

    ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER("zeppelin.impersonate.spark.proxy.user", true),
    ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL("zeppelin.notebook.git.remote.url", ""),
    ZEPPELIN_NOTEBOOK_GIT_REMOTE_USERNAME("zeppelin.notebook.git.remote.username", "token"),
    ZEPPELIN_NOTEBOOK_GIT_REMOTE_ACCESS_TOKEN("zeppelin.notebook.git.remote.access-token", ""),
    ZEPPELIN_NOTEBOOK_GIT_REMOTE_ORIGIN("zeppelin.notebook.git.remote.origin", "origin"),
    ZEPPELIN_NOTEBOOK_COLLABORATIVE_MODE_ENABLE("zeppelin.notebook.collaborative.mode.enable",
            true),
    ZEPPELIN_NOTEBOOK_CRON_ENABLE("zeppelin.notebook.cron.enable", false),
    ZEPPELIN_NOTEBOOK_CRON_FOLDERS("zeppelin.notebook.cron.folders", null),
    ZEPPELIN_PROXY_URL("zeppelin.proxy.url", null),
    ZEPPELIN_PROXY_USER("zeppelin.proxy.user", null),
    ZEPPELIN_PROXY_PASSWORD("zeppelin.proxy.password", null),
    ZEPPELIN_SEARCH_ENABLE("zeppelin.search.enable", true),
    ZEPPELIN_SEARCH_INDEX_REBUILD("zeppelin.search.index.rebuild", false),
    ZEPPELIN_SEARCH_USE_DISK("zeppelin.search.use.disk", true),
    ZEPPELIN_SEARCH_INDEX_PATH("zeppelin.search.index.path", "/tmp/zeppelin-index"),
    ZEPPELIN_JOBMANAGER_ENABLE("zeppelin.jobmanager.enable", false),
    ZEPPELIN_SPARK_ONLY_YARN_CLUSTER("zeppelin.spark.only_yarn_cluster", false),
    ZEPPELIN_SESSION_CHECK_INTERVAL("zeppelin.session.check_interval", 60 * 10 * 1000),
    ZEPPELIN_NOTE_FILE_EXCLUDE_FIELDS("zeppelin.note.file.exclude.fields", "");

    private String varName;
    private Class<?> varClass;
    private String stringValue;
    private int intValue;
    private float floatValue;
    private boolean booleanValue;
    private long longValue;


    ConfVars(String varName, String varValue) {
      this.varName = varName;
      this.varClass = String.class;
      this.stringValue = varValue;
      this.intValue = -1;
      this.floatValue = -1;
      this.longValue = -1;
      this.booleanValue = false;
    }

    ConfVars(String varName, int intValue) {
      this.varName = varName;
      this.varClass = Integer.class;
      this.stringValue = null;
      this.intValue = intValue;
      this.floatValue = -1;
      this.longValue = -1;
      this.booleanValue = false;
    }

    ConfVars(String varName, long longValue) {
      this.varName = varName;
      this.varClass = Integer.class;
      this.stringValue = null;
      this.intValue = -1;
      this.floatValue = -1;
      this.longValue = longValue;
      this.booleanValue = false;
    }

    ConfVars(String varName, float floatValue) {
      this.varName = varName;
      this.varClass = Float.class;
      this.stringValue = null;
      this.intValue = -1;
      this.longValue = -1;
      this.floatValue = floatValue;
      this.booleanValue = false;
    }

    ConfVars(String varName, boolean booleanValue) {
      this.varName = varName;
      this.varClass = Boolean.class;
      this.stringValue = null;
      this.intValue = -1;
      this.longValue = -1;
      this.floatValue = -1;
      this.booleanValue = booleanValue;
    }

    public String getVarName() {
      return varName;
    }

    public Class<?> getVarClass() {
      return varClass;
    }

    public int getIntValue() {
      return intValue;
    }

    public long getLongValue() {
      return longValue;
    }

    public float getFloatValue() {
      return floatValue;
    }

    public String getStringValue() {
      return stringValue;
    }

    public boolean getBooleanValue() {
      return booleanValue;
    }
  }
}
