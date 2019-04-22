---
layout: page
title: "Apache Zeppelin Configuration"
description: "This page will guide you to configure Apache Zeppelin using either environment variables or Java properties. Also, you can configure SSL for Zeppelin."
group: setup/operation 
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Apache Zeppelin Configuration

<div id="toc"></div>

## Zeppelin Properties
There are two locations you can configure Apache Zeppelin.

* **Environment variables** can be defined `conf/zeppelin-env.sh`(`conf\zeppelin-env.cmd` for Windows).
* **Java properties** can be defined in `conf/zeppelin-site.xml`.

If both are defined, then the **environment variables** will take priority.
> Mouse hover on each property and click <i class="fa fa-link fa-flip-horizontal"></i> then you can get a link for that.

<table class="table-configuration">
  <tr>
    <th>zeppelin-env.sh</th>
    <th>zeppelin-site.xml</th>
    <th>Default value</th>
    <th class="col-md-4">Description</th>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_PORT</h6></td>
    <td><h6 class="properties">zeppelin.server.port</h6></td>
    <td>8080</td>
    <td>Zeppelin server port </br>
      <span style="font-style:italic; color: gray"> Note: Please make sure you're not using the same port with
      <a href="https://zeppelin.apache.org/contribution/webapplication.html#dev-mode" target="_blank">Zeppelin web application development port</a> (default: 9000).</span></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_PORT</h6></td>
    <td><h6 class="properties">zeppelin.server.ssl.port</h6></td>
    <td>8443</td>
    <td>Zeppelin Server ssl port (used when ssl environment/property is set to true)</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_JMX_ENABLE</h6></td>
    <td><h6 class="properties">N/A</h6></td>
    <td></td>
    <td>Enable JMX by defining "true"</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_JMX_PORT</h6></td>
    <td><h6 class="properties">N/A</h6></td>
    <td>9996</td>
    <td>Port number which JMX uses</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_MEM</h6></td>
    <td>N/A</td>
    <td>-Xmx1024m -XX:MaxPermSize=512m</td>
    <td>JVM mem options</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_INTP_MEM</h6></td>
    <td>N/A</td>
    <td>ZEPPELIN_MEM</td>
    <td>JVM mem options for interpreter process</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_JAVA_OPTS</h6></td>
    <td>N/A</td>
    <td></td>
    <td>JVM options</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_ALLOWED_ORIGINS</h6></td>
    <td><h6 class="properties">zeppelin.server.allowed.origins</h6></td>
    <td>*</td>
    <td>Enables a way to specify a ',' separated list of allowed origins for REST and websockets. <br /> e.g. http://localhost:8080</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_CREDENTIALS_PERSIST</h6></td>
    <td><h6 class="properties">zeppelin.credentials.persist</h6></td>
    <td>true</td>
    <td>Persist credentials on a JSON file (credentials.json)</td>
  </tr>  
  <tr>
    <td><h6 class="properties">ZEPPELIN_CREDENTIALS_ENCRYPT_KEY</h6></td>
    <td><h6 class="properties">zeppelin.credentials.encryptKey</h6></td>
    <td></td>
    <td>If provided, encrypt passwords on the credentials.json file (passwords will be stored as plain-text otherwise</td>
  </tr>  
  <tr>
    <td>N/A</td>
    <td><h6 class="properties">zeppelin.anonymous.allowed</h6></td>
    <td>true</td>
    <td>The anonymous user is allowed by default.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SERVER_CONTEXT_PATH</h6></td>
    <td><h6 class="properties">zeppelin.server.context.path</h6></td>
    <td>/</td>
    <td>Context path of the web application</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_COLLABORATIVE_MODE_ENABLE</h6></td>
    <td><h6 class="properties">zeppelin.notebook.collaborative.mode.enable</h6></td>
    <td>true</td>
    <td>Enable basic opportunity for collaborative editing. Does not change the logic of operation if the note is used by one person.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL</h6></td>
    <td><h6 class="properties">zeppelin.ssl</h6></td>
    <td>false</td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_CLIENT_AUTH</h6></td>
    <td><h6 class="properties">zeppelin.ssl.client.auth</h6></td>
    <td>false</td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_KEYSTORE_PATH</h6></td>
    <td><h6 class="properties">zeppelin.ssl.keystore.path</h6></td>
    <td>keystore</td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_KEYSTORE_TYPE</h6></td>
    <td><h6 class="properties">zeppelin.ssl.keystore.type</h6></td>
    <td>JKS</td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_KEYSTORE_PASSWORD</h6></td>
    <td><h6 class="properties">zeppelin.ssl.keystore.password</h6></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_KEY_MANAGER_PASSWORD</h6></td>
    <td><h6 class="properties">zeppelin.ssl.key.manager.password</h6></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_TRUSTSTORE_PATH</h6></td>
    <td><h6 class="properties">zeppelin.ssl.truststore.path</h6></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_TRUSTSTORE_TYPE</h6></td>
    <td><h6 class="properties">zeppelin.ssl.truststore.type</h6></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SSL_TRUSTSTORE_PASSWORD</h6></td>
    <td><h6 class="properties">zeppelin.ssl.truststore.password</h6></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_HOMESCREEN</h6></td>
    <td><h6 class="properties">zeppelin.notebook.homescreen</h6></td>
    <td></td>
    <td>Display note IDs on the Apache Zeppelin homescreen <br />e.g. 2A94M5J1Z</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE</h6></td>
    <td><h6 class="properties">zeppelin.notebook.homescreen.hide</h6></td>
    <td>false</td>
    <td>Hide the note ID set by <code>ZEPPELIN_NOTEBOOK_HOMESCREEN</code> on the Apache Zeppelin homescreen. <br />For the further information, please read <a href="../usage/other_features/customizing_homepage.html">Customize your Zeppelin homepage</a>.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_WAR_TEMPDIR</h6></td>
    <td><h6 class="properties">zeppelin.war.tempdir</h6></td>
    <td>webapps</td>
    <td>Location of the jetty temporary directory</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_DIR</h6></td>
    <td><h6 class="properties">zeppelin.notebook.dir</h6></td>
    <td>notebook</td>
    <td>The root directory where notebook directories are saved</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_BUCKET</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.bucket</h6></td>
    <td>zeppelin</td>
    <td>S3 Bucket where notebook files will be saved</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_USER</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.user</h6></td>
    <td>user</td>
    <td>User name of an S3 bucket<br />e.g. <code>bucket/user/notebook/2A94M5J1Z/note.json</code></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_ENDPOINT</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.endpoint</h6></td>
    <td>s3.amazonaws.com</td>
    <td>Endpoint for the bucket</td>
  </tr>
  <tr>
    <td>N/A</td>
    <td><h6 class="properties">zeppelin.notebook.s3.timeout</h6></td>
    <td>120000</td>
    <td>Bucket endpoint request timeout in msec</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.kmsKeyID</h6></td>
    <td></td>
    <td>AWS KMS Key ID to use for encrypting data in S3 (optional)</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_EMP</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.encryptionMaterialsProvider</h6></td>
    <td></td>
    <td>Class name of a custom S3 encryption materials provider implementation to use for encrypting data in S3 (optional)</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_SSE</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.sse</h6></td>
    <td>false</td>
    <td>Save notebooks to S3 with server-side encryption enabled</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_S3_SIGNEROVERRIDE</h6></td>
    <td><h6 class="properties">zeppelin.notebook.s3.signerOverride</h6></td>
    <td></td>
    <td>Optional override to control which signature algorithm should be used to sign AWS requests</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING</h6></td>
    <td><h6 class="properties">zeppelin.notebook.azure.connectionString</h6></td>
    <td></td>
    <td>The Azure storage account connection string<br />e.g. <br/><code>DefaultEndpointsProtocol=https;<br/>AccountName=&lt;accountName&gt;;<br/>AccountKey=&lt;accountKey&gt;</code></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_AZURE_SHARE</h6></td>
    <td><h6 class="properties">zeppelin.notebook.azure.share</h6></td>
    <td>zeppelin</td>
    <td>Azure Share where the notebook files will be saved</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_AZURE_USER</h6></td>
    <td><h6 class="properties">zeppelin.notebook.azure.user</h6></td>
    <td>user</td>
    <td>Optional user name of an Azure file share<br />e.g. <code>share/user/notebook/2A94M5J1Z/note.json</code></td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_STORAGE</h6></td>
    <td><h6 class="properties">zeppelin.notebook.storage</h6></td>
    <td>org.apache.zeppelin.notebook.repo.GitNotebookRepo</td>
    <td>Comma separated list of notebook storage locations</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC</h6></td>
    <td><h6 class="properties">zeppelin.notebook.one.way.sync</h6></td>
    <td>false</td>
    <td>If there are multiple notebook storage locations, should we treat the first one as the only source of truth?</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_PUBLIC</h6></td>
    <td><h6 class="properties">zeppelin.notebook.public</h6></td>
    <td>true</td>
    <td>Make notebook public (set only <code>owners</code>) by default when created/imported. If set to <code>false</code> will add <code>user</code> to <code>readers</code> and <code>writers</code> as well, making it private and invisible to other users unless permissions are granted.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_INTERPRETER_DIR</h6></td>
    <td><h6 class="properties">zeppelin.interpreter.dir</h6></td>
    <td>interpreter</td>
    <td>Interpreter directory</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_INTERPRETER_DEP_MVNREPO</h6></td>
    <td><h6 class="properties">zeppelin.interpreter.dep.mvnRepo</h6></td>
    <td>http://repo1.maven.org/maven2/</td>
    <td>Remote principal repository for interpreter's additional dependency loading</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_INTERPRETER_OUTPUT_LIMIT</h6></td>
    <td><h6 class="properties">zeppelin.interpreter.output.limit</h6></td>
    <td>102400</td>
    <td>Output message from interpreter exceeding the limit will be truncated</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT</h6></td>
    <td><h6 class="properties">zeppelin.interpreter.connect.timeout</h6></td>
    <td>30000</td>
    <td>Output message from interpreter exceeding the limit will be truncated</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_DEP_LOCALREPO</h6></td>
    <td><h6 class="properties">zeppelin.dep.localrepo</h6></td>
    <td>local-repo</td>
    <td>Local repository for dependency loader.<br>ex)visualiztion modules of npm.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_HELIUM_NODE_INSTALLER_URL</h6></td>
    <td><h6 class="properties">zeppelin.helium.node.installer.url</h6></td>
    <td>https://nodejs.org/dist/</td>
    <td>Remote Node installer url for Helium dependency loader</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_HELIUM_NPM_INSTALLER_URL</h6></td>
    <td><h6 class="properties">zeppelin.helium.npm.installer.url</h6></td>
    <td>http://registry.npmjs.org/</td>
    <td>Remote Npm installer url for Helium dependency loader</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_HELIUM_YARNPKG_INSTALLER_URL</h6></td>
    <td><h6 class="properties">zeppelin.helium.yarnpkg.installer.url</h6></td>
    <td>https://github.com/yarnpkg/yarn/releases/download/</td>
    <td>Remote Yarn package installer url for Helium dependency loader</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE</h6></td>
    <td><h6 class="properties">zeppelin.websocket.max.text.message.size</h6></td>
    <td>1024000</td>
    <td>Size(in characters) of the maximum text message that can be received by websocket.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED</h6></td>
    <td><h6 class="properties">zeppelin.server.default.dir.allowed</h6></td>
    <td>false</td>
    <td>Enable directory listings on server.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL</h6></td>
    <td><h6 class="properties">zeppelin.notebook.git.remote.url</h6></td>
    <td></td>
    <td>GitHub's repository URL. It could be either the HTTP URL or the SSH URL. For example git@github.com:apache/zeppelin.git</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_GIT_REMOTE_USERNAME</h6></td>
    <td><h6 class="properties">zeppelin.notebook.git.remote.username</h6></td>
    <td>token</td>
    <td>GitHub username. By default it is `token` to use GitHub's API</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_GIT_REMOTE_ACCESS_TOKEN</h6></td>
    <td><h6 class="properties">zeppelin.notebook.git.remote.access-token</h6></td>
    <td>token</td>
    <td>GitHub access token to use GitHub's API. If username/password combination is used and not GitHub API, then this value is the password</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_NOTEBOOK_GIT_REMOTE_ORIGIN</h6></td>
    <td><h6 class="properties">zeppelin.notebook.git.remote.origin</h6></td>
    <td>token</td>
    <td>GitHub remote name. Default is `origin`</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_RUN_MODE</h6></td>
    <td><h6 class="properties">zeppelin.run.mode</h6></td>
    <td>auto</td>
    <td>Run mode. 'auto|local|k8s|yarn'. 'auto' autodetect environment. 'local' runs interpreter as a local process. 'k8s' runs interpreter on Kubernetes cluster. 'yarn' runs interpreter on Yarn cluster.</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_K8S_PORTFORWARD</h6></td>
    <td><h6 class="properties">zeppelin.k8s.portforward</h6></td>
    <td>false</td>
    <td>Port forward to interpreter rpc port. Set 'true' only on local development when zeppelin.k8s.mode 'on'. Don't use 'true' on production environment</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_K8S_CONTAINER_IMAGE</h6></td>
    <td><h6 class="properties">zeppelin.k8s.container.image</h6></td>
    <td>apache/zeppelin:{{ site.ZEPPELIN_VERSION }}</td>
    <td>Docker image for interpreters</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_K8S_SPARK_CONTAINER_IMAGE</h6></td>
    <td><h6 class="properties">zeppelin.k8s.spark.container.image</h6></td>
    <td>apache/spark:latest</td>
    <td>Docker image for Spark executors</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_K8S_TEMPLATE_DIR</h6></td>
    <td><h6 class="properties">zeppelin.k8s.template.dir</h6></td>
    <td>k8s</td>
    <td>Kubernetes yaml spec files</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_YARN_WEBAPP_ADDRESS</h6></td>
    <td><h6 class="properties">zeppelin.yarn.webapp.address</h6></td>
    <td></td>
    <td>Yarn webapp address, The zeppelin server gets the state of the interpreter container through this webapp</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_YARN_CONTAINER_IMAGE</h6></td>
    <td><h6 class="properties">zeppelin.yarn.container.image</h6></td>
    <td>apache/zeppelin:{{ site.ZEPPELIN_VERSION }}</td>
    <td>Docker image for interpreters</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_YARN_CONTAINER_RESOURCE</h6></td>
    <td><h6 class="properties">zeppelin.yarn.container.resource</h6></td>
    <td>memory=8G,vcores=1,gpu=0</td>
    <td>Docker default resource for interpreters container</td>
  </tr>
  <tr>
    <td><h6 class="properties">ZEPPELIN_YARN_CONTAINER_${INTERPRETER_SETTING_NAME}_RESOURCE</h6></td>
    <td><h6 class="properties">zeppelin.yarn.container.${INTERPRETER_SETTING_NAME}.resource</h6></td>
    <td>memory=8G,vcores=1,gpu=0</td>
    <td>Set different resources for different interpreters, e.g. zeppelin.yarn.container.python.resource</td>
  </tr>
</table>


## SSL Configuration

Enabling SSL requires a few configuration changes. First, you need to create certificates and then update necessary configurations to enable server side SSL and/or client side certificate authentication.

### Creating and configuring the Certificates

Information how about to generate certificates and a keystore can be found [here](https://wiki.eclipse.org/Jetty/Howto/Configure_SSL).

A condensed example can be found in the top answer to this [StackOverflow post](http://stackoverflow.com/questions/4008837/configure-ssl-on-jetty).

The keystore holds the private key and certificate on the server end. The trustore holds the trusted client certificates. Be sure that the path and password for these two stores are correctly configured in the password fields below. They can be obfuscated using the Jetty password tool. After Maven pulls in all the dependency to build Zeppelin, one of the Jetty jars contain the Password tool. Invoke this command from the Zeppelin home build directory with the appropriate version, user, and password.

```bash
java -cp ./zeppelin-server/target/lib/jetty-all-server-<version>.jar \
org.eclipse.jetty.util.security.Password <user> <password>
```

If you are using a self-signed, a certificate signed by an untrusted CA, or if client authentication is enabled, then the client must have a browser create exceptions for both the normal HTTPS port and WebSocket port. This can by done by trying to establish an HTTPS connection to both ports in a browser (e.g. if the ports are 443 and 8443, then visit https://127.0.0.1:443 and https://127.0.0.1:8443). This step can be skipped if the server certificate is signed by a trusted CA and client auth is disabled.

### Configuring server side SSL

The following properties needs to be updated in the `zeppelin-site.xml` in order to enable server side SSL.

```xml
<property>
  <name>zeppelin.server.ssl.port</name>
  <value>8443</value>
  <description>Server ssl port. (used when ssl property is set to true)</description>
</property>

<property>
  <name>zeppelin.ssl</name>
  <value>true</value>
  <description>Should SSL be used by the servers?</description>
</property>

<property>
  <name>zeppelin.ssl.keystore.path</name>
  <value>keystore</value>
  <description>Path to keystore relative to Zeppelin configuration directory</description>
</property>

<property>
  <name>zeppelin.ssl.keystore.type</name>
  <value>JKS</value>
  <description>The format of the given keystore (e.g. JKS or PKCS12)</description>
</property>

<property>
  <name>zeppelin.ssl.keystore.password</name>
  <value>change me</value>
  <description>Keystore password. Can be obfuscated by the Jetty Password tool</description>
</property>

<property>
  <name>zeppelin.ssl.key.manager.password</name>
  <value>change me</value>
  <description>Key Manager password. Defaults to keystore password. Can be obfuscated.</description>
</property>
```


### Enabling client side certificate authentication

The following properties needs to be updated in the `zeppelin-site.xml` in order to enable client side certificate authentication.

```xml
<property>
  <name>zeppelin.server.ssl.port</name>
  <value>8443</value>
  <description>Server ssl port. (used when ssl property is set to true)</description>
</property>

<property>
  <name>zeppelin.ssl.client.auth</name>
  <value>true</value>
  <description>Should client authentication be used for SSL connections?</description>
</property>

<property>
  <name>zeppelin.ssl.truststore.path</name>
  <value>truststore</value>
  <description>Path to truststore relative to Zeppelin configuration directory. Defaults to the keystore path</description>
</property>

<property>
  <name>zeppelin.ssl.truststore.type</name>
  <value>JKS</value>
  <description>The format of the given truststore (e.g. JKS or PKCS12). Defaults to the same type as the keystore type</description>
</property>

<property>
  <name>zeppelin.ssl.truststore.password</name>
  <value>change me</value>
  <description>Truststore password. Can be obfuscated by the Jetty Password tool. Defaults to the keystore password</description>
</property>
```

### Storing user credentials

In order to avoid having to re-enter credentials every time you restart/redeploy Zeppelin, you can store the user credentials. Zeppelin supports this via the ZEPPELIN_CREDENTIALS_PERSIST configuration.

Please notice that passwords will be stored in *plain text* by default. To encrypt the passwords, use the ZEPPELIN_CREDENTIALS_ENCRYPT_KEY config variable. This will encrypt passwords using the AES-128 algorithm.

You can generate an appropriate encryption key any way you'd like - for instance, by using the openssl tool:

```bash
openssl enc -aes-128-cbc -k secret -P -md sha1
```

*Important*: storing your encryption key in a configuration file is _not advised_. Depending on your environment security needs, you may want to consider utilizing a credentials server, storing the ZEPPELIN_CREDENTIALS_ENCRYPT_KEY as an OS env variable, or any other approach that would not colocate the encryption key and the encrypted content (the credentials.json file).


### Obfuscating Passwords using the Jetty Password Tool

Security best practices advise to not use plain text passwords and Jetty provides a password tool to help obfuscating the passwords used to access the KeyStore and TrustStore.

The Password tool documentation can be found [here](http://www.eclipse.org/jetty/documentation/current/configuring-security-secure-passwords.html).

After using the tool:

```bash
java -cp $ZEPPELIN_HOME/zeppelin-server/target/lib/jetty-util-9.2.15.v20160210.jar \
         org.eclipse.jetty.util.security.Password  \
         password

2016-12-15 10:46:47.931:INFO::main: Logging initialized @101ms
password
OBF:1v2j1uum1xtv1zej1zer1xtn1uvk1v1v
MD5:5f4dcc3b5aa765d61d8327deb882cf99
```

update your configuration with the obfuscated password :

```xml
<property>
  <name>zeppelin.ssl.keystore.password</name>
  <value>OBF:1v2j1uum1xtv1zej1zer1xtn1uvk1v1v</value>
  <description>Keystore password. Can be obfuscated by the Jetty Password tool</description>
</property>
```

### Create GitHub Access Token

When using GitHub to track notebooks, one can use GitHub's API for authentication. To create an access token, please use the following link https://github.com/settings/tokens.
The value of the access token generated is set in the `zeppelin.notebook.git.remote.access-token` property.

**Note:** After updating these configurations, Zeppelin server needs to be restarted.
