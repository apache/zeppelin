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

curl -O http://www.nflabs.com/pub/zeppelin/zeppelin-0.2.0.tar.gz

tar -xzf zeppelin-0.2.0.tar.gz
echo "HADOOP_HOME=`pwd`/hadoop-1.2.1" >> zeppelin-0.2.0/conf/zeppelin-env.sh

./zeppelin-0.2.0/bin/zeppelin-daemon.sh start

```

You can access Zeppelin with browser http://localhost:8080


### Prerequisites

* Java 1.6 or Later
* [Apache Hadoop](http://hadoop.apache.org/releases.html#Download) (Standalone mode)

### Download
To get Zeppelin distribution, download a recent release.

### Install
Unpack the downloaded Zeppelin distribution.


### Configure
Configuration can be done by both environment variable and java properties. If both defined, environment vaiable is used.
<table class="table-configuration">
  <tr>
    <th>zepplin-env.sh</th>
    <th>zepplin-site.xml</th>
    <th>Default value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>ZEPPELIN\_HOME</td>
    <td></td>
    <td></td>
    <td>Zeppelin Home directory</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_PORT</td>
    <td>zeppelin.server.port</td>
    <td>8080</td>
    <td>Zeppelin server port</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_JOB\_DIR</td>
    <td>zeppelin.job.dir</td>
    <td>sessions</td>
    <td>Zeppelin persist/load session in this directory. Can be a path or a URI. location on HDFS supported</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_ZAN\_REPO</td>
    <td>zeppelin.zan.repo</td>
    <td>https://github.com/NFLabs/zan</td>
    <td>Remote ZAN repository URL</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_ZAN\_LOCAL\_REPO</td>
    <td>zeppelin.zan.localrepo</td>
    <td>zan-repo</td>
    <td>Zeppelin library local repository. Local filesystem path</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_ZAN\_SHARED\_REPO</td>
    <td>zeppelin.zan.sharedrepo</td>
    <td></td>
    <td>Zeppelin library shared repository. Location on HDFS. Usufull when your backend (eg. hiveserver) is not running on the sam machine and want to use zeppelin library with resource file(eg. in hive 'ADD FILE 'path'). So your backend can get resource file from shared repository.</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_DRIVER</td>
    <td>zeppelin.driver.class</td>
    <td>com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver</td>
    <td>Zeppelin Driver class</td>
  </tr>
  <tr>
    <td>HIVE\_CONNECTION\_URI</td>
    <td>hive.connection.uri</td>
    <td>jdbc:hive2://</td>
    <td>Hive jdbc connection uri. Used for connecting to hive server 
Driver specific configuration : Hive Driver (com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver).
       eg. jdbc:hive2://localhost:10000/default, jdbc:hive://localhost:10000/default       
    </td>
  </tr>
</table>

Place your core-site.xml, hdfs-site.xml, hive-site.xml in _conf_ directory as you need. Zeppelin will include them in classpath on startup.

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


