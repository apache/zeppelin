---
layout: page
title: "Apache Zeppelin Configuration"
description: "This page will guide you to configure Apache Zeppelin using either environment variables or Java properties. Also, you can configure SSL for Zeppelin."
group: install
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
* **Java properties** can ba defined in `conf/zeppelin-site.xml`.

If both are defined, then the **environment variables** will take priority.

<table class="table-configuration">
  <tr>
    <th>zeppelin-env.sh</th>
    <th>zeppelin-site.xml</th>
    <th>Default value</th>
    <th class="col-md-4">Description</th>
  </tr>
  <tr>
    <td>ZEPPELIN_PORT</td>
    <td>zeppelin.server.port</td>
    <td>8080</td>
    <td>Zeppelin server port</td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_PORT</td>
    <td>zeppelin.server.ssl.port</td>
    <td>8443</td>
    <td>Zeppelin Server ssl port (used when ssl environment/property is set to true)</td>
  </tr>
  <tr>
    <td>ZEPPELIN_MEM</td>
    <td>N/A</td>
    <td>-Xmx1024m -XX:MaxPermSize=512m</td>
    <td>JVM mem options</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTP_MEM</td>
    <td>N/A</td>
    <td>ZEPPELIN_MEM</td>
    <td>JVM mem options for interpreter process</td>
  </tr>
  <tr>
    <td>ZEPPELIN_JAVA_OPTS</td>
    <td>N/A</td>
    <td></td>
    <td>JVM options</td>
  </tr>
  <tr>
    <td>ZEPPELIN_ALLOWED_ORIGINS</td>
    <td>zeppelin.server.allowed.origins</td>
    <td>*</td>
    <td>Enables a way to specify a ',' separated list of allowed origins for REST and websockets. <br /> e.g. http://localhost:8080 </td>
  </tr>
    <tr>
    <td>N/A</td>
    <td>zeppelin.anonymous.allowed</td>
    <td>true</td>
    <td>The anonymous user is allowed by default.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_SERVER_CONTEXT_PATH</td>
    <td>zeppelin.server.context.path</td>
    <td>/</td>
    <td>Context path of the web application</td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL</td>
    <td>zeppelin.ssl</td>
    <td>false</td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_CLIENT_AUTH</td>
    <td>zeppelin.ssl.client.auth</td>
    <td>false</td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_KEYSTORE_PATH</td>
    <td>zeppelin.ssl.keystore.path</td>
    <td>keystore</td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_KEYSTORE_TYPE</td>
    <td>zeppelin.ssl.keystore.type</td>
    <td>JKS</td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_KEYSTORE_PASSWORD</td>
    <td>zeppelin.ssl.keystore.password</td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_KEY_MANAGER_PASSWORD</td>
    <td>zeppelin.ssl.key.manager.password</td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_TRUSTSTORE_PATH</td>
    <td>zeppelin.ssl.truststore.path</td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_TRUSTSTORE_TYPE</td>
    <td>zeppelin.ssl.truststore.type</td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_SSL_TRUSTSTORE_PASSWORD</td>
    <td>zeppelin.ssl.truststore.password</td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_HOMESCREEN</td>
    <td>zeppelin.notebook.homescreen</td>
    <td></td>
    <td>Display note IDs on the Apache Zeppelin homescreen <br />e.g. 2A94M5J1Z</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE</td>
    <td>zeppelin.notebook.homescreen.hide</td>
    <td>false</td>
    <td>Hide the note ID set by <code>ZEPPELIN_NOTEBOOK_HOMESCREEN</code> on the Apache Zeppelin homescreen. <br />For the further information, please read <a href="../manual/notebookashomepage.html">Customize your Zeppelin homepage</a>.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_WAR_TEMPDIR</td>
    <td>zeppelin.war.tempdir</td>
    <td>webapps</td>
    <td>Location of the jetty temporary directory</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_DIR</td>
    <td>zeppelin.notebook.dir</td>
    <td>notebook</td>
    <td>The root directory where notebook directories are saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_BUCKET</td>
    <td>zeppelin.notebook.s3.bucket</td>
    <td>zeppelin</td>
    <td>S3 Bucket where notebook files will be saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_USER</td>
    <td>zeppelin.notebook.s3.user</td>
    <td>user</td>
    <td>User name of an S3 bucket<br />e.g. <code>bucket/user/notebook/2A94M5J1Z/note.json</code></td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_ENDPOINT</td>
    <td>zeppelin.notebook.s3.endpoint</td>
    <td>s3.amazonaws.com</td>
    <td>Endpoint for the bucket</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID</td>
    <td>zeppelin.notebook.s3.kmsKeyID</td>
    <td></td>
    <td>AWS KMS Key ID to use for encrypting data in S3 (optional)</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_EMP</td>
    <td>zeppelin.notebook.s3.encryptionMaterialsProvider</td>
    <td></td>
    <td>Class name of a custom S3 encryption materials provider implementation to use for encrypting data in S3 (optional)</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING</td>
    <td>zeppelin.notebook.azure.connectionString</td>
    <td></td>
    <td>The Azure storage account connection string<br />e.g. <br/><code>DefaultEndpointsProtocol=https;<br/>AccountName=&lt;accountName&gt;;<br/>AccountKey=&lt;accountKey&gt;</code></td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_AZURE_SHARE</td>
    <td>zeppelin.notebook.azure.share</td>
    <td>zeppelin</td>
    <td>Azure Share where the notebook files will be saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_AZURE_USER</td>
    <td>zeppelin.notebook.azure.user</td>
    <td>user</td>
    <td>Optional user name of an Azure file share<br />e.g. <code>share/user/notebook/2A94M5J1Z/note.json</code></td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_STORAGE</td>
    <td>zeppelin.notebook.storage</td>
    <td>org.apache.zeppelin.notebook.repo.VFSNotebookRepo</td>
    <td>Comma separated list of notebook storage locations</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC</td>
    <td>zeppelin.notebook.one.way.sync</td>
    <td>false</td>
    <td>If there are multiple notebook storage locations, should we treat the first one as the only source of truth?</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_PUBLIC</td>
    <td>zeppelin.notebook.public</td>
    <td>true</td>
    <td>Make notebook public (set only <code>owners</code>) by default when created/imported. If set to <code>false</code> will add <code>user</code> to <code>readers</code> and <code>writers</code> as well, making it private and invisible to other users unless permissions are granted.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETERS</td>
    <td>zeppelin.interpreters</td>
  <description></description>
    <td>org.apache.zeppelin.spark.SparkInterpreter,<br />org.apache.zeppelin.spark.PySparkInterpreter,<br />org.apache.zeppelin.spark.SparkSqlInterpreter,<br />org.apache.zeppelin.spark.DepInterpreter,<br />org.apache.zeppelin.markdown.Markdown,<br />org.apache.zeppelin.shell.ShellInterpreter,<br />
    ...
    </td>
    <td>
      Comma separated interpreter configurations [Class] <br/><br />
      <span style="font-style:italic; color: gray">NOTE: This property is deprecated since Zeppelin-0.6.0 and will not be supported from Zeppelin-0.7.0.</span>
    </td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETER_DIR</td>
    <td>zeppelin.interpreter.dir</td>
    <td>interpreter</td>
    <td>Interpreter directory</td>
  </tr>
  <tr>
    <td>ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE</td>
    <td>zeppelin.websocket.max.text.message.size</td>
    <td>1024000</td>
    <td>Size (in characters) of the maximum text message that can be received by websocket.</td>
  </tr>
</table>


## SSL Configuration

Enabling SSL requires a few configuration changes. First, you need to create certificates and then update necessary configurations to enable server side SSL and/or client side certificate authentication.

### Creating and configuring the Certificates

Information how about to generate certificates and a keystore can be found [here](https://wiki.eclipse.org/Jetty/Howto/Configure_SSL).

A condensed example can be found in the top answer to this [StackOverflow post](http://stackoverflow.com/questions/4008837/configure-ssl-on-jetty).

The keystore holds the private key and certificate on the server end. The trustore holds the trusted client certificates. Be sure that the path and password for these two stores are correctly configured in the password fields below. They can be obfuscated using the Jetty password tool. After Maven pulls in all the dependency to build Zeppelin, one of the Jetty jars contain the Password tool. Invoke this command from the Zeppelin home build directory with the appropriate version, user, and password.

```
java -cp ./zeppelin-server/target/lib/jetty-all-server-<version>.jar org.eclipse.jetty.util.security.Password <user> <password>
```

If you are using a self-signed, a certificate signed by an untrusted CA, or if client authentication is enabled, then the client must have a browser create exceptions for both the normal HTTPS port and WebSocket port. This can by done by trying to establish an HTTPS connection to both ports in a browser (e.g. if the ports are 443 and 8443, then visit https://127.0.0.1:443 and https://127.0.0.1:8443). This step can be skipped if the server certificate is signed by a trusted CA and client auth is disabled.

### Configuring server side SSL

The following properties needs to be updated in the `zeppelin-site.xml` in order to enable server side SSL.

```
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

```
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


### Obfuscating Passwords using the Jetty Password Tool

Security best practices advise to not use plain text passwords and Jetty provides a password tool to help obfuscating the passwords used to access the KeyStore and TrustStore.
 
The Password tool documentation can be found [here](http://www.eclipse.org/jetty/documentation/current/configuring-security-secure-passwords.html).

After using the tool:

```
java -cp $ZEPPELIN_HOME/zeppelin-server/target/lib/jetty-util-9.2.15.v20160210.jar \
         org.eclipse.jetty.util.security.Password  \
         password

2016-12-15 10:46:47.931:INFO::main: Logging initialized @101ms
password
OBF:1v2j1uum1xtv1zej1zer1xtn1uvk1v1v
MD5:5f4dcc3b5aa765d61d8327deb882cf99
```

update your configuration with the obfuscated password :

```
<property>
  <name>zeppelin.ssl.keystore.password</name>
  <value>OBF:1v2j1uum1xtv1zej1zer1xtn1uvk1v1v</value>
  <description>Keystore password. Can be obfuscated by the Jetty Password tool</description>
</property>
```


**Note:** After updating these configurations, Zeppelin server needs to be restarted.