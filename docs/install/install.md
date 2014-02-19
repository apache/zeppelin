---
layout: page
title: "Install Zeppelin"
description: ""
group: install
---
{% include JB/setup %}

#### Instructions for the Impatient

Install Zeppelin in local mode

```
# this scripts install hadoop and zeppelin in current directory and start zeppelin in local mode
# download and unarchive hadoop distribution package
curl -O http://apache.mirror.cdnetworks.com/hadoop/common/hadoop-1.2.1/hadoop-1.2.1-bin.tar.gz
tar -xzf hadoop-1.2.1-bin.tar.gz

# download zeppelin and unarchive
curl -O https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.0.tar.gz
tar -xzf zeppelin-0.3.0.tar.gz

# set HADOOP_HOME
echo "export HADOOP_HOME=`pwd`/hadoop-1.2.1" >> zeppelin-0.3.0/conf/zeppelin-env.sh

# start zeppelin
./zeppelin-0.3.0/bin/zeppelin-daemon.sh start

```

You can access Zeppelin with browser http://localhost:8080

------------------------


## Install

Configuring Zeppelin with existing hadoop cluster, refer this section.

### Prerequisites

* Java 1.6 or Later
* [Apache Hadoop](http://hadoop.apache.org/releases.html#Download) (Standalone mode)

### Download
To get Zeppelin distribution, download a recent release.

### Install
Unpack the downloaded Zeppelin distribution.


## Configure
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
    <td>jobs</td>
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
    <td>ZEPPELIN\_DRIVERS</td>
    <td>zeppelin.drivers</td>
    <td>hive:hive2://,exec:exec://</td>
    <td>Comma separated list of [Name]:[Connection URI]</td>
  </tr>
  <tr>
    <td>ZEPPELIN\_DRIVER\_DIR</td>
    <td>zeppelin.driver.dir</td>
    <td>drivers</td>
    <td>Zeppelin driver directory.
    </td>
  </tr>
</table>


### Configuring with existing Hive

If you have hive already installed in your hadoop cluster, just run hive server and make Zeppelin to connect it.
There're two different version of hive servers, [Hive Server1](https://cwiki.apache.org/confluence/display/Hive/HiveServer), [Hive Server2](https://cwiki.apache.org/confluence/display/Hive/Setting+Up+HiveServer2#SettingUpHiveServer2-HiveServer2). Make sure you have Hive server running.

And then, add connection uri in zeppelin.drivers at zeppelin-site.xml
If you have Hive Server 1 installed and running on host hiveserver1Address on port 10000, configuration property can be

```
<property>
  <name>zeppelin.drivers</name>
  <value>hive:hive://hiveserver1Address:10000/default,exec:exec://</value>
  <description>Comma separated driver configurations uri. </description>
</property>
```

If Hive Server 2 installed and running on host hiveserver2Address on port 10000, configuration will be

```
<property>
  <name>zeppelin.drivers</name>
  <value>hive:hive2://hiveserver2Address:10000/default,exec:exec://</value>
  <description>Comma separated driver configurations uri. </description>
</property>
```



## Start/Stop
#### Start Zeppelin

```
bin/zeppelin-daemon.sh start
```
After successful start, visit http://localhost:8080 with your web browser

#### Stop Zeppelin

```
bin/zeppelin-daemon.sh stop
```


