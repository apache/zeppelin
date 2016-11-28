---
layout: page
title: "Quick Start"
description: "This page will help you get started and will guide you through installing Apache Zeppelin, running it in the command line and configuring options."
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

# Quick Start

<div id="toc"></div>

Welcome to Apache Zeppelin! On this page are instructions to help you get started.

## Installation

Apache Zeppelin officially supports and is tested on the following environments:

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>Oracle JDK</td>
    <td>1.7 <br /> (set <code>JAVA_HOME</code>)</td>
  </tr>
  <tr>
    <td>OS</td>
    <td>Mac OSX <br /> Ubuntu 14.X <br /> CentOS 6.X <br /> Windows 7 Pro SP1</td>
  </tr>
</table>

### Downloading Binary Package

Two binary packages are available on the [Apache Zeppelin Download Page](http://zeppelin.apache.org/download.html). Only difference between these two binaries is interpreters are included in the package file.

- #### Package with `all` interpreters.

  Just unpack it in a directory of your choice and you're ready to go.

- #### Package with `net-install` interpreters.

  Unpack and follow [install additional interpreters](../manual/interpreterinstallation.html) to install interpreters. If you're unsure, just run `./bin/install-interpreter.sh --all` and install all interpreters.

## Starting Apache Zeppelin from the Command Line
#### Starting Apache Zeppelin

On all unix like platforms:

```
bin/zeppelin-daemon.sh start
```

If you are on Windows:

```
bin\zeppelin.cmd
```

After Zeppelin has started successfully, go to [http://localhost:8080](http://localhost:8080) with your web browser.

#### Stopping Zeppelin

```
bin/zeppelin-daemon.sh stop
```

## Next Steps

Congratulations, you have successfully installed Apache Zeppelin! Here are few steps you might find useful:

#### New to Apache Zeppelin...
 * For an in-depth overview, head to [Explore Apache Zeppelin UI](../quickstart/explorezeppelinui.html).
 * And then, try run [tutorial](http://localhost:8080/#/notebook/2A94M5J1Z) notebook in your Zeppelin.
 * And see how to change [configurations](#apache-zeppelin-configuration) like port number, etc.

#### Zeppelin with Apache Spark ...
 * To know more about deep integration with [Apache Spark](http://spark.apache.org/), check [Spark Interpreter](../interpreter/spark.html).

#### Zeppelin with JDBC data sources ...
 * Check [JDBC Interpreter](../interpreter/jdbc.html) to know more about configure and uses multiple JDBC data sources.

#### Zeppelin with Python ...
 * Check [Python interpreter](../interpreter/python.html) to know more about Matplotlib, Pandas, Conda/Docker environment integration.


#### Multi-user environment ...
 * Turn on [authentication](../security/shiroauthentication.html).
 * Manage your [notebook permission](../security/notebook_authorization.html).
 * For more informations, go to **More** -> **Security** section.

#### Other useful informations ...
 * Learn how [Display System](../displaysystem/basicdisplaysystem.html) works.
 * Use [Service Manager](#start-apache-zeppelin-with-a-service-manager) to start Zeppelin.
 * If you're using previous version please see [Upgrade Zeppelin version](./upgrade.html).


## Apache Zeppelin Configuration

You can configure Apache Zeppelin with either **environment variables** in `conf/zeppelin-env.sh` (`conf\zeppelin-env.cmd` for Windows) or **Java properties** in `conf/zeppelin-site.xml`. If both are defined, then the **environment variables** will take priority.

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
    <td>Enables a way to specify a ',' separated list of allowed origins for REST and websockets. <br /> i.e. http://localhost:8080 </td>
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
    <td>Display note IDs on the Apache Zeppelin homescreen <br />i.e. 2A94M5J1Z</td>
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
    <td>User name of an S3 bucket<br />i.e. <code>bucket/user/notebook/2A94M5J1Z/note.json</code></td>
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
    <td>The Azure storage account connection string<br />i.e. <br/><code>DefaultEndpointsProtocol=https;<br/>AccountName=&lt;accountName&gt;;<br/>AccountKey=&lt;accountKey&gt;</code></td>
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
    <td>Optional user name of an Azure file share<br />i.e. <code>share/user/notebook/2A94M5J1Z/note.json</code></td>
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
    <td>Make notebook public (set only `owners`) by default when created/imported. If set to `false` will add `user` to `readers` and `writers` as well, making it private and invisible to other users unless permissions are granted.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETERS</td>
    <td>zeppelin.interpreters</td>
  <description></description>
    <td>org.apache.zeppelin.spark.SparkInterpreter,<br />org.apache.zeppelin.spark.PySparkInterpreter,<br />org.apache.zeppelin.spark.SparkSqlInterpreter,<br />org.apache.zeppelin.spark.DepInterpreter,<br />org.apache.zeppelin.markdown.Markdown,<br />org.apache.zeppelin.shell.ShellInterpreter,<br />
    ...
    </td>
    <td>
      Comma separated interpreter configurations [Class] <br/>
      <span style="font-style:italic">NOTE: This property is deprecated since Zeppelin-0.6.0 and will not be supported from Zeppelin-0.7.0 on.</span>
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


#### Start Apache Zeppelin with a service manager

> **Note :** The below description was written based on Ubuntu Linux.

Apache Zeppelin can be auto-started as a service with an init script, using a service manager like **upstart**.

This is an example upstart script saved as `/etc/init/zeppelin.conf`
This allows the service to be managed with commands such as

```
sudo service zeppelin start  
sudo service zeppelin stop  
sudo service zeppelin restart
```

Other service managers could use a similar approach with the `upstart` argument passed to the `zeppelin-daemon.sh` script.

```
bin/zeppelin-daemon.sh upstart
```

**zeppelin.conf**

```
description "zeppelin"

start on (local-filesystems and net-device-up IFACE!=lo)
stop on shutdown

# Respawn the process on unexpected termination
respawn

# respawn the job up to 7 times within a 5 second period.
# If the job exceeds these values, it will be stopped and marked as failed.
respawn limit 7 5

# zeppelin was installed in /usr/share/zeppelin in this example
chdir /usr/share/zeppelin
exec bin/zeppelin-daemon.sh upstart
```


## Building from Source

If you want to build from source instead of using binary package, follow the instructions [here](./build.html).
