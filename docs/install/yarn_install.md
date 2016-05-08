---
layout: page
title: "Install Zeppelin to connect with existing YARN cluster"
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

## Introduction
This page describes how to pre-configure a bare metal node, configure Zeppelin and connect it to existing YARN cluster running Hortonworks flavour of Hadoop. It also describes steps to configure Spark & Hive interpreter of Zeppelin.

## Prepare Node

### Zeppelin user (Optional)
This step is optional, however its nice to run Zeppelin under its own user. In case you do not like to use Zeppelin (hope not) the user could be deleted along with all the packages that were installed for Zeppelin, Zeppelin binary itself and associated directories.

Create a zeppelin user and switch to zeppelin user or if zeppelin user is already created then login as zeppelin.

```bash
useradd zeppelin
su - zeppelin
whoami
```
Assuming a zeppelin user is created then running whoami command must return

```bash
zeppelin
```

Its assumed in the rest of the document that zeppelin user is indeed created and below installation instructions are performed as zeppelin user.

### List of Prerequisites

 * CentOS 6.x, Mac OSX, Ubuntu 14.X
 * Java 1.7
 * Hadoop client
 * Spark
 * Internet connection is required.

It's assumed that the node has CentOS 6.x installed on it. Although any version of Linux distribution should work fine.

#### Hadoop client
Zeppelin can work with multiple versions & distributions of Hadoop. A complete list is available [here](https://github.com/apache/incubator-zeppelin#build). This document assumes Hadoop 2.7.x client libraries including configuration files are installed on Zeppelin node. It also assumes /etc/hadoop/conf contains various Hadoop configuration files. The location of Hadoop configuration files may vary, hence use appropriate location.

```bash
hadoop version
Hadoop 2.7.1.2.3.1.0-2574
Subversion git@github.com:hortonworks/hadoop.git -r f66cf95e2e9367a74b0ec88b2df33458b6cff2d0
Compiled by jenkins on 2015-07-25T22:36Z
Compiled with protoc 2.5.0
From source with checksum 54f9bbb4492f92975e84e390599b881d
This command was run using /usr/hdp/2.3.1.0-2574/hadoop/lib/hadoop-common-2.7.1.2.3.1.0-2574.jar
```

#### Spark
Spark is supported out of the box and to take advantage of this, you need to Download appropriate version of Spark binary packages from [Spark Download page](http://spark.apache.org/downloads.html) and unzip it.
Zeppelin can work with multiple versions of Spark. A complete list is available [here](https://github.com/apache/incubator-zeppelin#build).
This document assumes Spark 1.6.0 is installed at /usr/lib/spark.
> Note: Spark should be installed on the same node as Zeppelin.

> Note: Spark's pre-built package for CDH 4 doesn't support yarn.

#### Zeppelin

Checkout source code from [git://git.apache.org/incubator-zeppelin.git](https://github.com/apache/incubator-zeppelin.git) or download binary package from [Download page](https://zeppelin.incubator.apache.org/download.html).
You can refer [Install](install.html) page for the details.
This document assumes that Zeppelin is located under `/home/zeppelin/incubator-zeppelin`.

## Zeppelin Configuration
Zeppelin configuration needs to be modified to connect to YARN cluster. Create a copy of zeppelin environment shell script.

```bash
cp /home/zeppelin/incubator-zeppelin/conf/zeppelin-env.sh.template /home/zeppelin/incubator-zeppelin/conf/zeppelin-env.sh
```

Set the following properties

```bash
export JAVA_HOME="/usr/java/jdk1.7.0_79"
export HADOOP_CONF_DIR="/etc/hadoop/conf"
export ZEPPELIN_JAVA_OPTS="-Dhdp.version=2.3.1.0-2574"
export SPARK_HOME="/usr/lib/spark"
```

As /etc/hadoop/conf contains various configurations of YARN cluster, Zeppelin can now submit Spark/Hive jobs on YARN cluster form its web interface. The value of hdp.version is set to 2.3.1.0-2574. This can be obtained by running the following command

```bash
hdp-select status hadoop-client | sed 's/hadoop-client - \(.*\)/\1/'
# It returned  2.3.1.0-2574
```

## Start/Stop
### Start Zeppelin

```
cd /home/zeppelin/incubator-zeppelin
bin/zeppelin-daemon.sh start
```
After successful start, visit http://[zeppelin-server-host-name]:8080 with your web browser.

### Stop Zeppelin

```
bin/zeppelin-daemon.sh stop
```

## Interpreter
Zeppelin provides various distributed processing frameworks to process data that ranges from Spark, Hive, Tajo, Ignite and Lens to name a few. This document describes to configure Hive & Spark interpreters.

### Hive
Zeppelin supports Hive interpreter and hence copy hive-site.xml that should be present at /etc/hive/conf to the configuration folder of Zeppelin. Once Zeppelin is built it will have conf folder under /home/zeppelin/incubator-zeppelin.

```bash
cp /etc/hive/conf/hive-site.xml  /home/zeppelin/incubator-zeppelin/conf
```

Once Zeppelin server has started successfully, visit http://[zeppelin-server-host-name]:8080 with your web browser. Click on Interpreter tab next to Notebook dropdown. Look for Hive configurations and set them appropriately. By default hive.hiveserver2.url will be pointing to localhost and hive.hiveserver2.password/hive.hiveserver2.user are set to hive/hive. Set them as per Hive installation on YARN cluster.
Click on Save button. Once these configurations are updated, Zeppelin will prompt you to restart the interpreter. Accept the prompt and the interpreter will reload the configurations.

### Spark
It was assumed that 1.6.0 version of Spark is installed at /usr/lib/spark. Look for Spark configurations and click edit button to add the following properties

<table class="table-configuration">
  <tr>
    <th>Property Name</th>
    <th>Property Value</th>
    <th>Remarks</th>
  </tr>
  <tr>
    <td>master</td>
    <td>yarn-client</td>
    <td>In yarn-client mode, the driver runs in the client process, and the application master is only used for requesting resources from YARN.</td>
  </tr>
  <tr>
    <td>spark.driver.extraJavaOptions</td>
    <td>-Dhdp.version=2.3.1.0-2574</td>
    <td></td>
  </tr>
  <tr>
    <td>spark.yarn.am.extraJavaOptions</td>
    <td>-Dhdp.version=2.3.1.0-2574</td>
    <td></td>
  </tr>
</table>

Click on Save button. Once these configurations are updated, Zeppelin will prompt you to restart the interpreter. Accept the prompt and the interpreter will reload the configurations.

Spark & Hive notebooks can be written with Zeppelin now. The resulting Spark & Hive jobs will run on configured YARN cluster.

## Debug
Zeppelin does not emit any kind of error messages on web interface when notebook/paragraph is run. If a paragraph fails it only displays ERROR. The reason for failure needs to be looked into log files which is present in logs directory under zeppelin installation base directory. Zeppelin creates a log file for each kind of interpreter.

```bash
[zeppelin@zeppelin-3529 logs]$ pwd
/home/zeppelin/incubator-zeppelin/logs
[zeppelin@zeppelin-3529 logs]$ ls -l
total 844
-rw-rw-r-- 1 zeppelin zeppelin  14648 Aug  3 14:45 zeppelin-interpreter-hive-zeppelin-zeppelin-3529.log
-rw-rw-r-- 1 zeppelin zeppelin 625050 Aug  3 16:05 zeppelin-interpreter-spark-zeppelin-zeppelin-3529.log
-rw-rw-r-- 1 zeppelin zeppelin 200394 Aug  3 21:15 zeppelin-zeppelin-zeppelin-3529.log
-rw-rw-r-- 1 zeppelin zeppelin  16162 Aug  3 14:03 zeppelin-zeppelin-zeppelin-3529.out
[zeppelin@zeppelin-3529 logs]$
```
