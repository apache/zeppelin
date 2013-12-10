---
layout: page
title: "Install Zeppelin"
description: ""
group: install
---
{% include JB/setup %}

### Instructions for the Impatient

Install Zeppelin in standalone mode

```
curl -O http://apache.mirror.cdnetworks.com/hadoop/common/hadoop-1.2.1/hadoop-1.2.1-bin.tar.gz
tar -xzf hadoop-1.2.1-bin.tar.gz

curl -O http://www.nflabs.com/pub/zeppelin/zeppelin-0.2.0-SNAPSHOT.tar.gz

mkdir zeppelin
tar -xzf zeppelin-0.2.0-SNAPSHOT.tar.gz --directory zeppelin

echo "HADOOP_HOME=`pwd`/hadoop-1.2.1" >> zeppelin/conf/zeppelin-env.sh

./zeppelin/bin/zeppelin-daemon.sh start

```

You can access Zeppelin with browser http://localhost:8080


### Prerequisites

* Java 1.6 or Later
* [Apache Hadoop](http://hadoop.apache.org/releases.html#Download) (Standalone mode)

### Download
To get Zeppelin distribution, download a recent release.

### Install
Unpack the downloaded Zeppelin distribution.


#### Common configuration

<table class="table-configuration">
  <tr>
    <th>zepplin-env.sh</th>
    <th>zepplin-site.xml</th>
    <th>Default value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>ZEPPELIN_HOME</td>
    <td></td>
    <td></td>
    <td>Zeppelin Home directory</td>
  </tr>
  <tr>
    <td>ZEPPELIN_PORT</td>
    <td>zeppelin.server.port</td>
    <td>8080</td>
    <td>Zeppelin server port</td>
  </tr>
  <tr>
    <td>ZEPPELIN_SESSION_DIR</td>
    <td>zeppelin.session.dir</td>
    <td>sessions</td>
    <td>Zeppelin persist/load session in this directory. Can be a path or a URI. location on HDFS supported</td>
  </tr>
  <tr>
    <td>ZEPPELIN_ZAN_LOCAL_REPO</td>
    <td>zeppelin.zan.localrepo</td>
    <td>zan-repo</td>
    <td>Zeppelin library local repository. Can be a path or a URI. location on HDFS supported</td>
  </tr>
  <tr>
    <td>ZEPPELIN_DRIVER</td>
    <td>zeppelin.driver.class</td>
    <td>com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver</td>
    <td>Zeppelin Driver class</td>
  </tr>
  <tr>
    <td>HIVE_CONNECTION_URI</td>
    <td>hive.connection.uri</td>
    <td></td>
    <td>Hive jdbc connection uri. Used for connecting to hive server 
Driver specific configuration : Hive Driver (com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver)</td>
  </tr>
</table>



### Start/Stop
#### Start Zeppelin

```
bin/zeppelin-daemon.sh start
```
After successful start, visit http://localhost:8080 with your web browser

#### Stop Zeppelin

```
bin/zeppelin-daemon.sh stop
```