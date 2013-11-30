---
layout: page
title: "How to contribute"
description: "How to contribute"
group: development
---


### Setting up
Here are some things you will need to build and test Zeppelin. 

#### Software Configuration Management(SCM)

Zeppelin uses Git for it's SCM system. Hosted by github.com. https://github.com/nflabs/zeppelin You'll need git client installed in your development machine. 

#### Integrated Development Environment(IDE)

You are free to use whatever IDE you prefer, or your favorite command line editor. 

#### Apache hadoop

Zeppelin is depends on Apache Hadoop. Downlaod a release from http://hadoop.apache.org/releases.html, and unarchive somewhere you can access. 

#### Build Tools

To build the code, install
Oracle Java 6
Apache Maven

### Getting the source code
First of all, you need the Zeppelin source code. The official location for Zeppelin is [https://github.com/nflabs/zeppelin](https://github.com/nflabs/zeppelin)

#### git access

Get the source code on your development machine using git.

{% highlight bash %}
git clone https://github.com/NFLabs/zeppelin.git zeppelin
{% endhighlight %}

You may also want to develop against a specific release. For example, for branch-0.1

{% highlight bash %}
git clone -b branch-0.1 https://github.com/NFLabs/zeppelin.git zeppelin
{% endhighlight %}


#### Fork repository

If you want not only build Zeppelin but also make change, then you need fork Zeppelin repository and make pull request.


###Build

{% highlight bash %}
mvn install
{% endhighlight %}

### Run Zepplin server in development mode
{% highlight bash %}
cd zeppelin-server
HADOOP_HOME=YOUR_HADOOP_HOME JAVA_HOME=YOUR_JAVA_HOME mvn exec:java -Dexec.mainClass="com.nflabs.zeppelin.server.ZeppelinServer" -Dexec.args=""
{% endhighlight %}
Server will be run on http://localhost:8080

### JIRA
Zeppelin manages it's issues in Jira. [https://zeppelin-project.atlassian.net/browse/ZEPPELIN](https://zeppelin-project.atlassian.net/browse/ZEPPELIN)

### Stay involved
Contributors should join the Zeppelin mailing lists.

* [https://groups.google.com/forum/#!forum/zeppelin-users](https://groups.google.com/forum/#!forum/zeppelin-users)
* [https://groups.google.com/forum/#!forum/zeppelin-developers](https://groups.google.com/forum/#!forum/zeppelin-developers)