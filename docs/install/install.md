---
layout: page
title: "Quick Start"
description: "This page will help you to get started and guide you through installation of Apache Zeppelin, running it in the command line and basic configuration options."
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
Welcome to your first trial to explore Apache Zeppelin! 
This page will help you to get started and here is the list of topics covered.

<div id="toc"></div>

## Installation

Apache Zeppelin officially supports and is tested on next environments.

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

There are two options to install Apache Zeppelin on your machine. One is [downloading pre-built binary package](#downloading-binary-package) from the archive. 
You can download not only the latest stable version but also the older one if you need. 
The other option is [building from the source](#building-from-source).
Although it can be unstable somehow since it is on development status, you can explore newly added feature and change it as you want.

### Downloading Binary Package

If you want to install Apache Zeppelin with a stable binary package, please visit [Apache Zeppelin download Page](http://zeppelin.apache.org/download.html). 

If you have downloaded `netinst` binary, [install additional interpreters](../manual/interpreterinstallation.html) before you start Zeppelin. Or simply run `./bin/install-interpreter.sh --all`.

After unpacking, jump to [Starting Apache Zeppelin with Command Line](#starting-apache-zeppelin-with-command-line) section.

### Building from Source
If you want to build from the source, the software below needs to be installed on your system.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>Git</td>
    <td></td>
  </tr>
  <tr>
    <td>Maven</td>
    <td>3.1.x or higher</td>
  </tr>
</table>

If you don't have it installed yet, please check [Before Build](https://github.com/apache/zeppelin/blob/master/README.md#before-build) section and follow step by step instructions from there.

####1. Clone Apache Zeppelin repository

```
git clone https://github.com/apache/zeppelin.git
```

####2. Build source with options 
Each interpreters requires different build options. For the further information about options, please see [Build](https://github.com/apache/zeppelin#build) section.

```
mvn clean package -DskipTests [Options]
```

Here are some examples with several options

```
# build with spark-2.0, scala-2.11
./dev/change_scala_version.sh 2.11
mvn clean package -Pspark-2.0 -Phadoop-2.4 -Pyarn -Ppyspark -Psparkr -Pscala-2.11

# build with spark-1.6, scala-2.10
mvn clean package -Pspark-1.6 -Phadoop-2.4 -Pyarn -Ppyspark -Psparkr

# spark-cassandra integration
mvn clean package -Pcassandra-spark-1.5 -Dhadoop.version=2.6.0 -Phadoop-2.6 -DskipTests

# with CDH
mvn clean package -Pspark-1.5 -Dhadoop.version=2.6.0-cdh5.5.0 -Phadoop-2.6 -Pvendor-repo -DskipTests

# with MapR
mvn clean package -Pspark-1.5 -Pmapr50 -DskipTests
```

For the further information about building with source, please see [README.md](https://github.com/apache/zeppelin/blob/master/README.md) in Zeppelin repository.

## Starting Apache Zeppelin with Command Line
#### Start Zeppelin

```
bin/zeppelin-daemon.sh start
```

If you are using Windows 

```
bin\zeppelin.cmd
```

After successful start, visit [http://localhost:8080](http://localhost:8080) with your web browser.

#### Stop Zeppelin

```
bin/zeppelin-daemon.sh stop
```

#### (Optional) Start Apache Zeppelin with a service manager

> **Note :** The below description was written based on Ubuntu Linux.

Apache Zeppelin can be auto started as a service with an init script, such as services managed by **upstart**.

The following is an example of upstart script to be saved as `/etc/init/zeppelin.conf`
This also allows the service to be managed with commands such as

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

## What is the next?
Congratulation on your successful Apache Zeppelin installation! Here are two next steps you might need.

#### If you are new to Apache Zeppelin
 * For an in-depth overview of Apache Zeppelin UI, head to [Explore Apache Zeppelin UI](../quickstart/explorezeppelinui.html).
 * After getting familiar with Apache Zeppelin UI, have fun with a short walk-through [Tutorial](../quickstart/tutorial.html) that uses Apache Spark backend.
 * If you need more configuration setting for Apache Zeppelin, jump to the next section: [Apache Zeppelin Configuration](#apache-zeppelin-configuration).
 
#### If you need more information about Spark or JDBC interpreter setting
 * Apache Zeppelin provides deep integration with [Apache Spark](http://spark.apache.org/). For the further information, see [Spark Interpreter for Apache Zeppelin](../interpreter/spark.html). 
 * Also, you can use generic JDBC connections in Apache Zeppelin. Go to [Generic JDBC Interpreter for Apache Zeppelin](../interpreter/jdbc.html).
 
#### If you are in multi-user environment
 * You can set permissions for your notebooks and secure data resource in multi-user environment. Go to **More** -> **Security** section.
   
## Apache Zeppelin Configuration

You can configure Apache Zeppelin with both **environment variables** in `conf/zeppelin-env.sh` (`conf\zeppelin-env.cmd` for Windows) and **Java properties** in `conf/zeppelin-site.xml`. If both are defined, then the **environment variables** will take priority.

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
    <td>Enables a way to specify a ',' separated list of allowed origins for rest and websockets. <br /> i.e. http://localhost:8080 </td>
  </tr>
    <tr>
    <td>N/A</td>
    <td>zeppelin.anonymous.allowed</td>
    <td>true</td>
    <td>Anonymous user is allowed by default.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_SERVER_CONTEXT_PATH</td>
    <td>zeppelin.server.context.path</td>
    <td>/</td>
    <td>A context path of the web application</td>
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
    <td>A notebook id displayed in Apache Zeppelin homescreen <br />i.e. 2A94M5J1Z</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE</td>
    <td>zeppelin.notebook.homescreen.hide</td>
    <td>false</td>
    <td>This value can be "true" when to hide the notebook id set by <code>ZEPPELIN_NOTEBOOK_HOMESCREEN</code> on the Apache Zeppelin homescreen. <br />For the further information, please read <a href="../manual/notebookashomepage.html">Customize your Zeppelin homepage</a>.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_WAR_TEMPDIR</td>
    <td>zeppelin.war.tempdir</td>
    <td>webapps</td>
    <td>A location of jetty temporary directory</td>
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
    <td>A user name of S3 bucket<br />i.e. <code>bucket/user/notebook/2A94M5J1Z/note.json</code></td>
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
    <td>Share where the notebook files will be saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_AZURE_USER</td>
    <td>zeppelin.notebook.azure.user</td>
    <td>user</td>
    <td>An optional user name of Azure file share<br />i.e. <code>share/user/notebook/2A94M5J1Z/note.json</code></td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_STORAGE</td>
    <td>zeppelin.notebook.storage</td>
    <td>org.apache.zeppelin.notebook.repo.VFSNotebookRepo</td>
    <td>Comma separated list of notebook storage</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC</td>
    <td>zeppelin.notebook.one.way.sync</td>
    <td>false</td>
    <td>If there are multiple notebook storages, should we treat the first one as the only source of truth?</td>
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
      <span style="font-style:italic">NOTE: This property is deprecated since Zeppelin-0.6.0 and will not be supported from Zeppelin-0.7.0</span>
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
    <td>Size in characters of the maximum text message to be received by websocket.</td>
  </tr>
</table>
