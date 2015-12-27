---
layout: page
title: "Install Zeppelin"
description: ""
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



## From binary package

   Download latest binary package from [Download](../download.html).


## Build from source

   Check instructions in [README](https://github.com/apache/incubator-zeppelin/blob/master/README.md) to build from source.



## Configure

Configuration can be done by both environment variable(conf/zeppelin-env.sh) and java properties(conf/zeppelin-site.xml). If both defined, environment vaiable is used.


<table class="table-configuration">
  <tr>
    <th>zepplin-env.sh</th>
    <th>zepplin-site.xml</th>
    <th>Default value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>ZEPPELIN_PORT</td>
    <td>zeppelin.server.port</td>
    <td>8080</td>
    <td>Zeppelin server port.</td>
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
    <td>JVM Options</td>
  </tr>
  <tr>
    <td>ZEPPELIN_ALLOWED_ORIGINS</td>
    <td>zeppelin.server.allowed.origins</td>
    <td>*</td>
    <td>Allows a way to specify a ',' separated list of allowed origins for rest and websockets. i.e. http://localhost:8080</td>
  </tr>
  <tr>
    <td>ZEPPELIN_SERVER_CONTEXT_PATH</td>
    <td>zeppelin.server.context.path</td>
    <td>/</td>
    <td>Context Path of the Web Application</td>
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
    <td>Id of notebook to be displayed in homescreen ex) 2A94M5J1Z</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE</td>
    <td>zeppelin.notebook.homescreen.hide</td>
    <td>false</td>
    <td>hide homescreen notebook from list when this value set to "true"</td>
  </tr>
  <tr>
    <td>ZEPPELIN_WAR_TEMPDIR</td>
    <td>zeppelin.war.tempdir</td>
    <td>webapps</td>
    <td>The location of jetty temporary directory.</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_DIR</td>
    <td>zeppelin.notebook.dir</td>
    <td>notebook</td>
    <td>Where notebook file is saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_BUCKET</td>
    <td>zeppelin.notebook.s3.bucket</td>
    <td>zeppelin</td>
    <td>Bucket where notebook saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_S3_USER</td>
    <td>zeppelin.notebook.s3.user</td>
    <td>user</td>
    <td>User in bucket where notebook saved. For example bucket/user/notebook/2A94M5J1Z/note.json</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_STORAGE</td>
    <td>zeppelin.notebook.storage</td>
    <td>org.apache.zeppelin.notebook.repo.VFSNotebookRepo</td>
    <td>Comma separated list of notebook storage</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETERS</td>
    <td>zeppelin.interpreters</td>
  <description></description>
    <td>org.apache.zeppelin.spark.SparkInterpreter,<br />org.apache.zeppelin.spark.PySparkInterpreter,<br />org.apache.zeppelin.spark.SparkSqlInterpreter,<br />org.apache.zeppelin.spark.DepInterpreter,<br />org.apache.zeppelin.markdown.Markdown,<br />org.apache.zeppelin.shell.ShellInterpreter,<br />org.apache.zeppelin.hive.HiveInterpreter<br />
    ...
    </td>
    <td>Comma separated interpreter configurations [Class]. First interpreter become a default</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETER_DIR</td>
    <td>zeppelin.interpreter.dir</td>
    <td>interpreter</td>
    <td>Zeppelin interpreter directory</td>
  </tr>
</table>

<br />
You'll also need to configure individual interpreter. Information can be found in 'Interpreter' section in this documentation.

For example [Spark](../interpreter/spark.html).

<br />
## Start/Stop
#### Start Zeppelin

```
bin/zeppelin-daemon.sh start
```
After successful start, visit http://localhost:8080 with your web browser.

#### Stop Zeppelin

```
bin/zeppelin-daemon.sh stop
```


