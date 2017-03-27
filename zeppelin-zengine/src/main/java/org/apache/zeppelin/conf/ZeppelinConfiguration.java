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

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.GitNotebookRepo;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin configuration.
 *
 */
public class ZeppelinConfiguration extends XMLConfiguration {
  private static final String ZEPPELIN_SITE_XML = "zeppelin-site.xml";
  private static final long serialVersionUID = 4749305895693848035L;
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinConfiguration.class);

  private static final String HELIUM_PACKAGE_DEFAULT_URL =
      "https://s3.amazonaws.com/helium-package/helium.json";
  private static ZeppelinConfiguration conf;

  public ZeppelinConfiguration(URL url) throws ConfigurationException {
    setDelimiterParsingDisabled(true);
    load(url);
  }

  public ZeppelinConfiguration() {
    ConfVars[] vars = ConfVars.values();
    for (ConfVars v : vars) {
      if (v.getType() == ConfVars.VarType.BOOLEAN) {
        this.setProperty(v.getVarName(), v.getBooleanValue());
      } else if (v.getType() == ConfVars.VarType.LONG) {
        this.setProperty(v.getVarName(), v.getLongValue());
      } else if (v.getType() == ConfVars.VarType.INT) {
        this.setProperty(v.getVarName(), v.getIntValue());
      } else if (v.getType() == ConfVars.VarType.FLOAT) {
        this.setProperty(v.getVarName(), v.getFloatValue());
      } else if (v.getType() == ConfVars.VarType.STRING) {
        this.setProperty(v.getVarName(), v.getStringValue());
      } else {
        throw new RuntimeException("Unsupported VarType");
      }
    }

  }


  /**
   * Load from resource.
   *url = ZeppelinConfiguration.class.getResource(ZEPPELIN_SITE_XML);
   * @throws ConfigurationException
   */
  public static synchronized ZeppelinConfiguration create() {
    if (conf != null) {
      return conf;
    }

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    URL url;

    url = ZeppelinConfiguration.class.getResource(ZEPPELIN_SITE_XML);
    if (url == null) {
      ClassLoader cl = ZeppelinConfiguration.class.getClassLoader();
      if (cl != null) {
        url = cl.getResource(ZEPPELIN_SITE_XML);
      }
    }
    if (url == null) {
      url = classLoader.getResource(ZEPPELIN_SITE_XML);
    }

    if (url == null) {
      LOG.warn("Failed to load configuration, proceeding with a default");
      conf = new ZeppelinConfiguration();
    } else {
      try {
        LOG.info("Load configuration from " + url);
        conf = new ZeppelinConfiguration(url);
      } catch (ConfigurationException e) {
        LOG.warn("Failed to load configuration from " + url + " proceeding with a default", e);
        conf = new ZeppelinConfiguration();
      }
    }

    LOG.info("Server Host: " + conf.getServerAddress());
    if (conf.useSsl() == false) {
      LOG.info("Server Port: " + conf.getServerPort());
    } else {
      LOG.info("Server SSL Port: " + conf.getServerSslPort());
    }
    LOG.info("Context Path: " + conf.getServerContextPath());
    LOG.info("Zeppelin Version: " + Util.getVersion());

    return conf;
  }


  private String getStringValue(String name, String d) {
    List<ConfigurationNode> properties = getRootNode().getChildren();
    if (properties == null || properties.isEmpty()) {
      return d;
    }
    for (ConfigurationNode p : properties) {
      if (p.getChildren("name") != null && !p.getChildren("name").isEmpty()
          && name.equals(p.getChildren("name").get(0).getValue())) {
        return (String) p.getChildren("value").get(0).getValue();
      }
    }
    return d;
  }

  private int getIntValue(String name, int d) {
    List<ConfigurationNode> properties = getRootNode().getChildren();
    if (properties == null || properties.isEmpty()) {
      return d;
    }
    for (ConfigurationNode p : properties) {
      if (p.getChildren("name") != null && !p.getChildren("name").isEmpty()
          && name.equals(p.getChildren("name").get(0).getValue())) {
        return Integer.parseInt((String) p.getChildren("value").get(0).getValue());
      }
    }
    return d;
  }

  private long getLongValue(String name, long d) {
    List<ConfigurationNode> properties = getRootNode().getChildren();
    if (properties == null || properties.isEmpty()) {
      return d;
    }
    for (ConfigurationNode p : properties) {
      if (p.getChildren("name") != null && !p.getChildren("name").isEmpty()
          && name.equals(p.getChildren("name").get(0).getValue())) {
        return Long.parseLong((String) p.getChildren("value").get(0).getValue());
      }
    }
    return d;
  }

  private float getFloatValue(String name, float d) {
    List<ConfigurationNode> properties = getRootNode().getChildren();
    if (properties == null || properties.isEmpty()) {
      return d;
    }
    for (ConfigurationNode p : properties) {
      if (p.getChildren("name") != null && !p.getChildren("name").isEmpty()
          && name.equals(p.getChildren("name").get(0).getValue())) {
        return Float.parseFloat((String) p.getChildren("value").get(0).getValue());
      }
    }
    return d;
  }

  private boolean getBooleanValue(String name, boolean d) {
    List<ConfigurationNode> properties = getRootNode().getChildren();
    if (properties == null || properties.isEmpty()) {
      return d;
    }
    for (ConfigurationNode p : properties) {
      if (p.getChildren("name") != null && !p.getChildren("name").isEmpty()
          && name.equals(p.getChildren("name").get(0).getValue())) {
        return Boolean.parseBoolean((String) p.getChildren("value").get(0).getValue());
      }
    }
    return d;
  }

  public String getString(ConfVars c) {
    return getString(c.name(), c.getVarName(), c.getStringValue());
  }

  public String getString(String envName, String propertyName, String defaultValue) {
    if (System.getenv(envName) != null) {
      return System.getenv(envName);
    }
    if (System.getProperty(propertyName) != null) {
      return System.getProperty(propertyName);
    }

    return getStringValue(propertyName, defaultValue);
  }

  public int getInt(ConfVars c) {
    return getInt(c.name(), c.getVarName(), c.getIntValue());
  }

  public int getInt(String envName, String propertyName, int defaultValue) {
    if (System.getenv(envName) != null) {
      return Integer.parseInt(System.getenv(envName));
    }

    if (System.getProperty(propertyName) != null) {
      return Integer.parseInt(System.getProperty(propertyName));
    }
    return getIntValue(propertyName, defaultValue);
  }

  public long getLong(ConfVars c) {
    return getLong(c.name(), c.getVarName(), c.getLongValue());
  }

  public long getLong(String envName, String propertyName, long defaultValue) {
    if (System.getenv(envName) != null) {
      return Long.parseLong(System.getenv(envName));
    }

    if (System.getProperty(propertyName) != null) {
      return Long.parseLong(System.getProperty(propertyName));
    }
    return getLongValue(propertyName, defaultValue);
  }

  public float getFloat(ConfVars c) {
    return getFloat(c.name(), c.getVarName(), c.getFloatValue());
  }

  public float getFloat(String envName, String propertyName, float defaultValue) {
    if (System.getenv(envName) != null) {
      return Float.parseFloat(System.getenv(envName));
    }
    if (System.getProperty(propertyName) != null) {
      return Float.parseFloat(System.getProperty(propertyName));
    }
    return getFloatValue(propertyName, defaultValue);
  }

  public boolean getBoolean(ConfVars c) {
    return getBoolean(c.name(), c.getVarName(), c.getBooleanValue());
  }

  public boolean getBoolean(String envName, String propertyName, boolean defaultValue) {
    if (System.getenv(envName) != null) {
      return Boolean.parseBoolean(System.getenv(envName));
    }

    if (System.getProperty(propertyName) != null) {
      return Boolean.parseBoolean(System.getProperty(propertyName));
    }
    return getBooleanValue(propertyName, defaultValue);
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
      return getRelativeDir(
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
      return getRelativeDir(
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

  public String getNotebookDir() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_DIR);
  }

  public String getUser() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_USER);
  }

  public String getBucketName() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_BUCKET);
  }

  public String getEndpoint() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT);
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

  public String getMongoUri() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_URI);
  }

  public String getMongoDatabase() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_DATABASE);
  }

  public String getMongoCollection() {
    return getString(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_COLLECTION);
  }

  public boolean getMongoAutoimport() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT);
  }

  public String getInterpreterListPath() {
    return getRelativeDir(String.format("%s/interpreter-list", getConfDir()));
  }

  public String getInterpreterDir() {
    return getRelativeDir(ConfVars.ZEPPELIN_INTERPRETER_DIR);
  }

  public String getInterpreterJson() {
    return getString(ConfVars.ZEPPELIN_INTERPRETER_JSON);
  }

  public String getInterpreterSettingPath() {
    return getRelativeDir(String.format("%s/interpreter.json", getConfDir()));
  }

  public String getHeliumConfPath() {
    return getRelativeDir(String.format("%s/helium.json", getConfDir()));
  }

  public String getHeliumRegistry() {
    return getRelativeDir(ConfVars.ZEPPELIN_HELIUM_REGISTRY);
  }

  public String getHeliumNpmRegistry() {
    return getString(ConfVars.ZEPPELIN_HELIUM_NPM_REGISTRY);
  }

  public String getNotebookAuthorizationPath() {
    return getRelativeDir(String.format("%s/notebook-authorization.json", getConfDir()));
  }

  public Boolean credentialsPersist() {
    return getBoolean(ConfVars.ZEPPELIN_CREDENTIALS_PERSIST);
  }

  public String getCredentialsPath() {
    return getRelativeDir(String.format("%s/credentials.json", getConfDir()));
  }

  public String getShiroPath() {
    String shiroPath = getRelativeDir(String.format("%s/shiro.ini", getConfDir()));
    return new File(shiroPath).exists() ? shiroPath : StringUtils.EMPTY;
  }

  public String getInterpreterRemoteRunnerPath() {
    return getRelativeDir(ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER);
  }

  public String getInterpreterLocalRepoPath() {
    return getRelativeDir(ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO);
  }

  public String getInterpreterMvnRepoPath() {
    return getString(ConfVars.ZEPPELIN_INTERPRETER_DEP_MVNREPO);
  }

  public String getRelativeDir(ConfVars c) {
    return getRelativeDir(getString(c));
  }

  public String getRelativeDir(String path) {
    if (path != null && path.startsWith("/") || isWindowsPath(path)) {
      return path;
    } else {
      return getString(ConfVars.ZEPPELIN_HOME) + "/" + path;
    }
  }

  public boolean isWindowsPath(String path){
    return path.matches("^[A-Za-z]:\\\\.*");
  }

  public boolean isAnonymousAllowed() {
    return getBoolean(ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED);
  }

  public boolean isNotebokPublic() {
    return getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC);
  }

  public String getConfDir() {
    return getString(ConfVars.ZEPPELIN_CONF_DIR);
  }

  public List<String> getAllowedOrigins()
  {
    if (getString(ConfVars.ZEPPELIN_ALLOWED_ORIGINS).isEmpty()) {
      return Arrays.asList(new String[0]);
    }

    return Arrays.asList(getString(ConfVars.ZEPPELIN_ALLOWED_ORIGINS).toLowerCase().split(","));
  }

  public String getWebsocketMaxTextMessageSize() {
    return getString(ConfVars.ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE);
  }

  public Map<String, String> dumpConfigurations(ZeppelinConfiguration conf,
                                                ConfigurationKeyPredicate predicate) {
    Map<String, String> configurations = new HashMap<>();

    for (ZeppelinConfiguration.ConfVars v : ZeppelinConfiguration.ConfVars.values()) {
      String key = v.getVarName();

      if (!predicate.apply(key)) {
        continue;
      }

      ConfVars.VarType type = v.getType();
      Object value = null;
      if (type == ConfVars.VarType.BOOLEAN) {
        value = conf.getBoolean(v);
      } else if (type == ConfVars.VarType.LONG) {
        value = conf.getLong(v);
      } else if (type == ConfVars.VarType.INT) {
        value = conf.getInt(v);
      } else if (type == ConfVars.VarType.FLOAT) {
        value = conf.getFloat(v);
      } else if (type == ConfVars.VarType.STRING) {
        value = conf.getString(v);
      }

      if (value != null) {
        configurations.put(key, value.toString());
      }
    }
    return configurations;
  }

  /**
   * Predication whether key/value pair should be included or not
   */
  public interface ConfigurationKeyPredicate {
    boolean apply(String key);
  }

  /**
   * Wrapper class.
   */
  public static enum ConfVars {
    ZEPPELIN_HOME("zeppelin.home", "./"),
    ZEPPELIN_ADDR("zeppelin.server.addr", "0.0.0.0"),
    ZEPPELIN_PORT("zeppelin.server.port", 8080),
    ZEPPELIN_SERVER_CONTEXT_PATH("zeppelin.server.context.path", "/"),
    ZEPPELIN_SSL("zeppelin.ssl", false),
    ZEPPELIN_SSL_PORT("zeppelin.server.ssl.port", 8443),
    ZEPPELIN_SSL_CLIENT_AUTH("zeppelin.ssl.client.auth", false),
    ZEPPELIN_SSL_KEYSTORE_PATH("zeppelin.ssl.keystore.path", "keystore"),
    ZEPPELIN_SSL_KEYSTORE_TYPE("zeppelin.ssl.keystore.type", "JKS"),
    ZEPPELIN_SSL_KEYSTORE_PASSWORD("zeppelin.ssl.keystore.password", ""),
    ZEPPELIN_SSL_KEY_MANAGER_PASSWORD("zeppelin.ssl.key.manager.password", null),
    ZEPPELIN_SSL_TRUSTSTORE_PATH("zeppelin.ssl.truststore.path", null),
    ZEPPELIN_SSL_TRUSTSTORE_TYPE("zeppelin.ssl.truststore.type", null),
    ZEPPELIN_SSL_TRUSTSTORE_PASSWORD("zeppelin.ssl.truststore.password", null),
    ZEPPELIN_WAR("zeppelin.war", "zeppelin-web/dist"),
    ZEPPELIN_WAR_TEMPDIR("zeppelin.war.tempdir", "webapps"),
    ZEPPELIN_INTERPRETERS("zeppelin.interpreters", "org.apache.zeppelin.spark.SparkInterpreter,"
        + "org.apache.zeppelin.spark.PySparkInterpreter,"
        + "org.apache.zeppelin.rinterpreter.RRepl,"
        + "org.apache.zeppelin.rinterpreter.KnitR,"
        + "org.apache.zeppelin.spark.SparkRInterpreter,"
        + "org.apache.zeppelin.spark.SparkSqlInterpreter,"
        + "org.apache.zeppelin.spark.DepInterpreter,"
        + "org.apache.zeppelin.markdown.Markdown,"
        + "org.apache.zeppelin.angular.AngularInterpreter,"
        + "org.apache.zeppelin.shell.ShellInterpreter,"
        + "org.apache.zeppelin.livy.LivySparkInterpreter,"
        + "org.apache.zeppelin.livy.LivySparkSQLInterpreter,"
        + "org.apache.zeppelin.livy.LivyPySparkInterpreter,"
        + "org.apache.zeppelin.livy.LivyPySpark3Interpreter,"
        + "org.apache.zeppelin.livy.LivySparkRInterpreter,"
        + "org.apache.zeppelin.alluxio.AlluxioInterpreter,"
        + "org.apache.zeppelin.file.HDFSFileInterpreter,"
        + "org.apache.zeppelin.pig.PigInterpreter,"
        + "org.apache.zeppelin.pig.PigQueryInterpreter,"
        + "org.apache.zeppelin.flink.FlinkInterpreter,"
        + "org.apache.zeppelin.python.PythonInterpreter,"
        + "org.apache.zeppelin.python.PythonInterpreterPandasSql,"
        + "org.apache.zeppelin.python.PythonCondaInterpreter,"
        + "org.apache.zeppelin.python.PythonDockerInterpreter,"
        + "org.apache.zeppelin.ignite.IgniteInterpreter,"
        + "org.apache.zeppelin.ignite.IgniteSqlInterpreter,"
        + "org.apache.zeppelin.lens.LensInterpreter,"
        + "org.apache.zeppelin.cassandra.CassandraInterpreter,"
        + "org.apache.zeppelin.geode.GeodeOqlInterpreter,"
        + "org.apache.zeppelin.kylin.KylinInterpreter,"
        + "org.apache.zeppelin.elasticsearch.ElasticsearchInterpreter,"
        + "org.apache.zeppelin.scalding.ScaldingInterpreter,"
        + "org.apache.zeppelin.jdbc.JDBCInterpreter,"
        + "org.apache.zeppelin.hbase.HbaseInterpreter,"
        + "org.apache.zeppelin.bigquery.BigQueryInterpreter,"
        + "org.apache.zeppelin.beam.BeamInterpreter,"
        + "org.apache.zeppelin.scio.ScioInterpreter,"
        + "org.apache.zeppelin.groovy.GroovyInterpreter"
        ),
    ZEPPELIN_INTERPRETER_JSON("zeppelin.interpreter.setting", "interpreter-setting.json"),
    ZEPPELIN_INTERPRETER_DIR("zeppelin.interpreter.dir", "interpreter"),
    ZEPPELIN_INTERPRETER_LOCALREPO("zeppelin.interpreter.localRepo", "local-repo"),
    ZEPPELIN_INTERPRETER_DEP_MVNREPO("zeppelin.interpreter.dep.mvnRepo",
        "http://repo1.maven.org/maven2/"),
    ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT("zeppelin.interpreter.connect.timeout", 30000),
    ZEPPELIN_INTERPRETER_MAX_POOL_SIZE("zeppelin.interpreter.max.poolsize", 10),
    ZEPPELIN_INTERPRETER_GROUP_ORDER("zeppelin.interpreter.group.order", "spark,md,angular,sh,"
        + "livy,alluxio,file,psql,flink,python,ignite,lens,cassandra,geode,kylin,elasticsearch,"
        + "scalding,jdbc,hbase,bigquery,beam,pig,scio,groovy"),
    ZEPPELIN_INTERPRETER_OUTPUT_LIMIT("zeppelin.interpreter.output.limit", 1024 * 100),
    ZEPPELIN_ENCODING("zeppelin.encoding", "UTF-8"),
    ZEPPELIN_NOTEBOOK_DIR("zeppelin.notebook.dir", "notebook"),
    // use specified notebook (id) as homescreen
    ZEPPELIN_NOTEBOOK_HOMESCREEN("zeppelin.notebook.homescreen", null),
    // whether homescreen notebook will be hidden from notebook list or not
    ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE("zeppelin.notebook.homescreen.hide", false),
    ZEPPELIN_NOTEBOOK_S3_BUCKET("zeppelin.notebook.s3.bucket", "zeppelin"),
    ZEPPELIN_NOTEBOOK_S3_ENDPOINT("zeppelin.notebook.s3.endpoint", "s3.amazonaws.com"),
    ZEPPELIN_NOTEBOOK_S3_USER("zeppelin.notebook.s3.user", "user"),
    ZEPPELIN_NOTEBOOK_S3_EMP("zeppelin.notebook.s3.encryptionMaterialsProvider", null),
    ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID("zeppelin.notebook.s3.kmsKeyID", null),
    ZEPPELIN_NOTEBOOK_S3_KMS_KEY_REGION("zeppelin.notebook.s3.kmsKeyRegion", null),
    ZEPPELIN_NOTEBOOK_S3_SSE("zeppelin.notebook.s3.sse", false),
    ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING("zeppelin.notebook.azure.connectionString", null),
    ZEPPELIN_NOTEBOOK_AZURE_SHARE("zeppelin.notebook.azure.share", "zeppelin"),
    ZEPPELIN_NOTEBOOK_AZURE_USER("zeppelin.notebook.azure.user", "user"),
    ZEPPELIN_NOTEBOOK_MONGO_DATABASE("zeppelin.notebook.mongo.database", "zeppelin"),
    ZEPPELIN_NOTEBOOK_MONGO_COLLECTION("zeppelin.notebook.mongo.collection", "notes"),
    ZEPPELIN_NOTEBOOK_MONGO_URI("zeppelin.notebook.mongo.uri", "mongodb://localhost"),
    ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT("zeppelin.notebook.mongo.autoimport", false),
    ZEPPELIN_NOTEBOOK_STORAGE("zeppelin.notebook.storage", GitNotebookRepo.class.getName()),
    ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC("zeppelin.notebook.one.way.sync", false),
    // whether by default note is public or private
    ZEPPELIN_NOTEBOOK_PUBLIC("zeppelin.notebook.public", true),
    ZEPPELIN_INTERPRETER_REMOTE_RUNNER("zeppelin.interpreter.remoterunner",
        System.getProperty("os.name")
                .startsWith("Windows") ? "bin/interpreter.cmd" : "bin/interpreter.sh"),
    // Decide when new note is created, interpreter settings will be binded automatically or not.
    ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING("zeppelin.notebook.autoInterpreterBinding", true),
    ZEPPELIN_CONF_DIR("zeppelin.conf.dir", "conf"),
    ZEPPELIN_DEP_LOCALREPO("zeppelin.dep.localrepo", "local-repo"),
    ZEPPELIN_HELIUM_REGISTRY("zeppelin.helium.registry", "helium," + HELIUM_PACKAGE_DEFAULT_URL),
    ZEPPELIN_HELIUM_NPM_REGISTRY("zeppelin.helium.npm.registry", "http://registry.npmjs.org/"),
    // Allows a way to specify a ',' separated list of allowed origins for rest and websockets
    // i.e. http://localhost:8080
    ZEPPELIN_ALLOWED_ORIGINS("zeppelin.server.allowed.origins", "*"),
    ZEPPELIN_ANONYMOUS_ALLOWED("zeppelin.anonymous.allowed", true),
    ZEPPELIN_CREDENTIALS_PERSIST("zeppelin.credentials.persist", true),
    ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE("zeppelin.websocket.max.text.message.size", "1024000"),
    ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED("zeppelin.server.default.dir.allowed", false);

    private String varName;
    @SuppressWarnings("rawtypes")
    private Class varClass;
    private String stringValue;
    private VarType type;
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
      this.type = VarType.STRING;
    }

    ConfVars(String varName, int intValue) {
      this.varName = varName;
      this.varClass = Integer.class;
      this.stringValue = null;
      this.intValue = intValue;
      this.floatValue = -1;
      this.longValue = -1;
      this.booleanValue = false;
      this.type = VarType.INT;
    }

    ConfVars(String varName, long longValue) {
      this.varName = varName;
      this.varClass = Integer.class;
      this.stringValue = null;
      this.intValue = -1;
      this.floatValue = -1;
      this.longValue = longValue;
      this.booleanValue = false;
      this.type = VarType.LONG;
    }

    ConfVars(String varName, float floatValue) {
      this.varName = varName;
      this.varClass = Float.class;
      this.stringValue = null;
      this.intValue = -1;
      this.longValue = -1;
      this.floatValue = floatValue;
      this.booleanValue = false;
      this.type = VarType.FLOAT;
    }

    ConfVars(String varName, boolean booleanValue) {
      this.varName = varName;
      this.varClass = Boolean.class;
      this.stringValue = null;
      this.intValue = -1;
      this.longValue = -1;
      this.floatValue = -1;
      this.booleanValue = booleanValue;
      this.type = VarType.BOOLEAN;
    }

    public String getVarName() {
      return varName;
    }

    @SuppressWarnings("rawtypes")
    public Class getVarClass() {
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

    public VarType getType() {
      return type;
    }

    enum VarType {
      STRING {
        @Override
        void checkType(String value) throws Exception {}
      },
      INT {
        @Override
        void checkType(String value) throws Exception {
          Integer.valueOf(value);
        }
      },
      LONG {
        @Override
        void checkType(String value) throws Exception {
          Long.valueOf(value);
        }
      },
      FLOAT {
        @Override
        void checkType(String value) throws Exception {
          Float.valueOf(value);
        }
      },
      BOOLEAN {
        @Override
        void checkType(String value) throws Exception {
          Boolean.valueOf(value);
        }
      };

      boolean isType(String value) {
        try {
          checkType(value);
        } catch (Exception e) {
          LOG.error("Exception in ZeppelinConfiguration while isType", e);
          return false;
        }
        return true;
      }

      String typeString() {
        return name().toUpperCase();
      }

      abstract void checkType(String value) throws Exception;
    }
  }
}
