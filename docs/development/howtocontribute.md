---
layout: page
title: "How to contribute"
description: "How to contribute"
group: development
---

## IMPORTANT

Apache Zeppelin (incubating) is an [Apache2 License](http://www.apache.org/licenses/LICENSE-2.0.html) Software.
Any contribution to Zeppelin (Source code, Documents, Image, Website) means you agree license all your contributions as Apache2 License.



### Setting up
Here are some things you will need to build and test Zeppelin. 

#### Software Configuration Management(SCM)

Zeppelin uses Git for it's SCM system. Hosted by github.com. https://github.com/nflabs/zeppelin You'll need git client installed in your development machine. 

#### Integrated Development Environment(IDE)

You are free to use whatever IDE you prefer, or your favorite command line editor. 

#### Build Tools

To build the code, install
Oracle Java 7
Apache Maven

### Getting the source code
First of all, you need the Zeppelin source code. The official location for Zeppelin is [https://github.com/nflabs/zeppelin](https://github.com/nflabs/zeppelin)

#### git access

Get the source code on your development machine using git.

```
git clone https://github.com/NFLabs/zeppelin.git zeppelin
```

You may also want to develop against a specific release. For example, for branch-0.1

```
git clone -b branch-0.1 https://github.com/NFLabs/zeppelin.git zeppelin
```


#### Fork repository

If you want not only build Zeppelin but also make change, then you need fork Zeppelin repository and make pull request.


###Build

```
mvn install
```

To skip test

```
mvn install -DskipTests
```

To build with specific spark / hadoop version

```
mvn install -Dspark.version=1.0.1 -Dhadoop.version=2.2.0
```

### Run Zepplin server in development mode

```
cd zeppelin-server
HADOOP_HOME=YOUR_HADOOP_HOME JAVA_HOME=YOUR_JAVA_HOME mvn exec:java -Dexec.mainClass="com.nflabs.zeppelin.server.ZeppelinServer" -Dexec.args=""
```

or use daemon script

```
bin/zeppelin-daemon start
```


Server will be run on http://localhost:8080

### JIRA
Zeppelin manages it's issues in Jira. [https://issues.apache.org/jira/browse/ZEPPELIN](https://issues.apache.org/jira/browse/ZEPPELIN)

### Stay involved
Contributors should join the Zeppelin mailing lists.

* [dev@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-dev/) is for people who want to contribute code to Zeppelin. [subscribe](mailto:dev-subscribe@zeppelin.incubator.apache.org?subject=send this email to subscribe), [unsubscribe](mailto:dev-unsubscribe@zeppelin.incubator.apache.org?subject=send this email to unsubscribe), [archives](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-dev/)
