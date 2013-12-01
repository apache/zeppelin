---
layout: page
title: "Install Zeppelin"
description: ""
group: install
---
{% include JB/setup %}

### Instructions for the Impatient

Install Zeppelin in standalone mode

{% highlight bash %}
curl -O http://apache.mirror.cdnetworks.com/hadoop/common/hadoop-1.2.1/hadoop-1.2.1-bin.tar.gz
tar -xzf hadoop-1.2.1-bin.tar.gz

curl -O http://www.nflabs.com/pub/zeppelin/zeppelin-0.2.0-SNAPSHOT.tar.gz

mkdir zeppelin
tar -xzf zeppelin-0.2.0-SNAPSHOT.tar.gz --directory zeppelin

echo "HADOOP_HOME=`pwd`/hadoop-1.2.1" >> zeppelin/conf/zeppelin-env.sh

./zeppelin/bin/zeppelin-daemon.sh start

{% endhighlight %}

You can access Zeppelin with browser http://localhost:8080


### Prerequisites

* Java 1.6 or Later
* [Apache Hadoop](http://hadoop.apache.org/releases.html#Download) (Standalone mode)

### Download
To get Zeppelin distribution, download a recent release.

### Install
Unpack the downloaded Zeppelin distribution.


#### Common configuration

| zepplin-env.sh | zepplin-site.xml | Default value | Description |
| -------------- | ---------------- | ------------- | ----------- |
| ZEPPELIN_HOME	 |                  |               | Zeppelin Home directory |
| ZEPPELIN_PORT  | zeppelin.server.port | 8080 | Zeppelin server port |
| ZEPPELIN_SESSION_DIR | zeppelin.session.dir | sessions | Zeppelin persist/load session in this directory. Can be a path or a URI. location on HDFS supported |
| ZEPPELIN_ZAN_LOCAL_REPO | zeppelin.zan.localrepo | zan-repo | Zeppelin library local repository. Can be a path or a URI. location on HDFS supported |
| ZEPPELIN_DRIVER | zeppelin.driver.class | com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver | Zeppelin Driver class |
| HIVE_CONNECTION_URI | hive.connection.uri | | Hive jdbc connection uri. Used for connecting to hive server 
Driver specific configuration : Hive Driver (com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver) |


### Start/Stop
#### Start Zeppelin

{% highlight bash %}
bin/zeppelin-daemon.sh start
{% endhighlight %}
After successful start, visit http://localhost:8080 with your web browser

#### Stop Zeppelin
{% highlight bash %}
bin/zeppelin-daemon.sh stop
{% endhighlight %}