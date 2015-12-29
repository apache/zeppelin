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
This page describes how to pre-configure a bare metal node, build & configure Zeppelin on it, configure Zeppelin and connect it to existing YARN cluster running Hortonworks flavour of Hadoop. It also describes steps to configure Spark & Hive interpreter of Zeppelin. 

## Prepare Node

### Zeppelin user (Optional)
This step is optional, however its nice to run Zeppelin under its own user. In case you do not like to use Zeppelin (hope not) the user could be deleted along with all the pacakges that were installed for Zeppelin, Zeppelin binary itself and associated directories.

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

 * CentOS 6.x
 * Git
 * Java 1.7 
 * Apache Maven
 * Hadoop client.
 * Spark.
 * Internet connection is required. 

Its assumed that the node has CentOS 6.x installed on it. Although any version of Linux distribution should work fine. The working directory of all prerequisite pacakges is /home/zeppelin/prerequisites, although any location could be used.

#### Git
Intall latest stable version of Git. This document describes installation of version 2.4.8

```bash
yum install curl-devel expat-devel gettext-devel openssl-devel zlib-devel
yum install  gcc perl-ExtUtils-MakeMaker
yum remove git
cd /home/zeppelin/prerequisites
wget https://github.com/git/git/archive/v2.4.8.tar.gz
tar xzf git-2.0.4.tar.gz
cd git-2.0.4
make prefix=/home/zeppelin/prerequisites/git all
make prefix=/home/zeppelin/prerequisites/git install
echo "export PATH=$PATH:/home/zeppelin/prerequisites/bin" >> /home/zeppelin/.bashrc
source /home/zeppelin/.bashrc
git --version
```

Assuming all the packages are successfully installed, running the version option with git command should display

```bash
git version 2.4.8
```

#### Java
Zeppelin works well with 1.7.x version of Java runtime. Download JDK version 7 and a stable update and follow below instructions to install it.

```bash
cd /home/zeppelin/prerequisites/
#Download JDK 1.7, Assume JDK 7 update 79 is downloaded.
tar -xf jdk-7u79-linux-x64.tar.gz
echo "export JAVA_HOME=/home/zeppelin/prerequisites/jdk1.7.0_79" >> /home/zeppelin/.bashrc
source /home/zeppelin/.bashrc
echo $JAVA_HOME
```
Assuming all the packages are successfully installed, echoing JAVA_HOME environment variable should display

```bash
/home/zeppelin/prerequisites/jdk1.7.0_79
```

#### Apache Maven
Download and install a stable version of Maven.

```bash
cd /home/zeppelin/prerequisites/
wget ftp://mirror.reverse.net/pub/apache/maven/maven-3/3.3.3/binaries/apache-maven-3.3.3-bin.tar.gz
tar -xf apache-maven-3.3.3-bin.tar.gz 
cd apache-maven-3.3.3
export MAVEN_HOME=/home/zeppelin/prerequisites/apache-maven-3.3.3
echo "export PATH=$PATH:/home/zeppelin/prerequisites/apache-maven-3.3.3/bin" >> /home/zeppelin/.bashrc
source /home/zeppelin/.bashrc
mvn -version
```

Assuming all the packages are successfully installed, running the version option with mvn command should display

```bash
Apache Maven 3.3.3 (7994120775791599e205a5524ec3e0dfe41d4a06; 2015-04-22T04:57:37-07:00)
Maven home: /home/zeppelin/prerequisites/apache-maven-3.3.3
Java version: 1.7.0_79, vendor: Oracle Corporation
Java home: /home/zeppelin/prerequisites/jdk1.7.0_79/jre
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "2.6.32-358.el6.x86_64", arch: "amd64", family: "unix"
```

#### Hadoop client
Zeppelin can work with multiple versions & distributions of Hadoop. A complete list [is available here.](https://github.com/apache/incubator-zeppelin#build) This document assumes Hadoop 2.7.x client libraries including configuration files are installed on Zeppelin node. It also assumes /etc/hadoop/conf contains various Hadoop configuration files. The location of Hadoop configuration files may vary, hence use appropriate location.

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
Zeppelin can work with multiple versions Spark. A complete list [is available here.](https://github.com/apache/incubator-zeppelin#build) This document assumes Spark 1.3.1 is installed on Zeppelin node at /home/zeppelin/prerequisites/spark.

## Build

Checkout source code from [https://github.com/apache/incubator-zeppelin](https://github.com/apache/incubator-zeppelin)

```bash
cd /home/zeppelin/
git clone https://github.com/apache/incubator-zeppelin.git
```
Zeppelin package is available at /home/zeppelin/incubator-zeppelin after the checkout completes.

### Cluster mode

As its assumed Hadoop 2.7.x is installed on the YARN cluster & Spark 1.3.1 is installed on Zeppelin node. Hence appropriate options are chosen to build Zeppelin. This is very important as Zeppelin will bundle corresponding Hadoop & Spark libraries and they must match the ones present on YARN cluster & Zeppelin Spark installation. 

Zeppelin is a maven project and hence must be built with Apache Maven.

```bash
cd /home/zeppelin/incubator-zeppelin
mvn clean package -Pspark-1.3 -Dspark.version=1.3.1 -Dhadoop.version=2.7.0 -Phadoop-2.6 -Pyarn -DskipTests
```
Building Zeppelin for first time downloads various dependencies and hence takes few minutes to complete. 

## Zeppelin Configuration
Zeppelin configurations needs to be modified to connect to YARN cluster. Create a copy of zeppelin environment XML

```bash
cp /home/zeppelin/incubator-zeppelin/conf/zeppelin-env.sh.template /home/zeppelin/incubator-zeppelin/conf/zeppelin-env.sh 
```

Set the following properties

```bash
export JAVA_HOME=/home/zeppelin/prerequisites/jdk1.7.0_79
export HADOOP_CONF_DIR=/etc/hadoop/conf
export ZEPPELIN_JAVA_OPTS="-Dhdp.version=2.3.1.0-2574"
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
Zeppelin provides to various distributed processing frameworks to process data that ranges from Spark, Hive, Tajo, Ignite and Lens to name a few. This document describes to configure Hive & Spark interpreters.

### Hive
Zeppelin supports Hive interpreter and hence copy hive-site.xml that should be present at /etc/hive/conf to the configuration folder of Zeppelin. Once Zeppelin is built it will have conf folder under /home/zeppelin/incubator-zeppelin.

```bash
cp /etc/hive/conf/hive-site.xml  /home/zeppelin/incubator-zeppelin/conf
```

Once Zeppelin server has started successfully, visit http://[zeppelin-server-host-name]:8080 with your web browser. Click on Interpreter tab next to Notebook dropdown. Look for Hive configurations and set them appropriately. By default hive.hiveserver2.url will be pointing to localhost and hive.hiveserver2.password/hive.hiveserver2.user are set to hive/hive. Set them as per Hive installation on YARN cluster. 
Click on Save button. Once these configurations are updated, Zeppelin will prompt you to restart the interpreter. Accept the prompt and the interpreter will reload the configurations.

### Spark
Zeppelin was built with Spark 1.3.1 and it was assumed that 1.3.1 version of Spark is installed at /home/zeppelin/prerequisites/spark. Look for Spark configrations and click edit button to add the following properties

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
    <td>spark.home</td>
    <td>/home/zeppelin/prerequisites/spark</td>
    <td></td>
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
  <tr>
    <td>spark.yarn.jar</td>
    <td>/home/zeppelin/incubator-zeppelin/interpreter/spark/zeppelin-spark-0.6.0-incubating-SNAPSHOT.jar</td>
    <td></td>
  </tr>
</table>

Click on Save button. Once these configurations are updated, Zeppelin will prompt you to restart the interpreter. Accept the prompt and the interpreter will reload the configurations.

Spark & Hive notebooks can be written with Zeppelin now. The resulting Spark & Hive jobs will run on configured YARN cluster.

## Debug
Zeppelin does not emit any kind of error messages on web interface when notebook/paragrah is run. If a paragraph fails it only displays ERROR. The reason for failure needs to be looked into log files which is present in logs directory under zeppelin installation base directory. Zeppelin creates a log file for each kind of interpreter.

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
