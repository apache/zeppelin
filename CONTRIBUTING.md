# How to contribute

**Zeppelin** is [Apache2 License](https://github.com/apache/incubator-zeppelin/blob/master/CONTRIBUTING.md) Software.
Contributing to Zeppelin (Source code, Documents, Image, Website) means you agree to the Apache2 License.

1. Make sure your issue is not already in the [Jira issue tracker](https://issues.apache.org/jira/browse/ZEPPELIN)
2. If not, create a ticket describing the change you're proposing in the [Jira issue tracker](https://issues.apache.org/jira/browse/ZEPPELIN)
3. Contribute your patch via Pull Request.


## Setting up
Here are some things you will need to build and test Zeppelin. 

### Software Configuration Management(SCM)

Zeppelin uses Git for it's SCM system. Hosted by github.com. `https://github.com/apache/incubator-zeppelin` You'll need git client installed in your development machine.

### Integrated Development Environment(IDE)

You are free to use whatever IDE you prefer, or your favorite command line editor. 

### Code convention
We are following Google Code style:
* [Java style](http://google-styleguide.googlecode.com/svn/trunk/javaguide.html) 
* [Shell style](https://google-styleguide.googlecode.com/svn/trunk/shell.xml)

Checkstyle report location are in `${submodule}/target/site/checkstyle.html`
Test coverage report location are in `${submodule}/target/site/cobertura/index.html`

#### Build Tools

To build the code, install
 * Oracle Java 7
 * Apache Maven

## Getting the source code
First of all, you need the Zeppelin source code. The official location for Zeppelin is [https://github.com/apache/incubator-zeppelin](https://github.com/apache/incubator-zeppelin)

### git access

Get the source code on your development machine using git.

```
git clone git@github.com:apache/incubator-zeppelin.git zeppelin
```

You may also want to develop against a specific release. For example, for branch-0.1

```
git clone -b branch-0.1 git@github.com:apache/incubator-zeppelin.git zeppelin
```


### Fork repository

If you want not only build Zeppelin but also make change, then you need fork Zeppelin repository and make pull request.


## Build

```
mvn install
```

To skip test

```
mvn install -DskipTests
```

To build with specific spark / hadoop version

```
mvn install -Phadoop-2.2 -Dhadoop.version=2.2.0 -Pspark-1.3 -Dspark.version=1.3.0
```

## Run Zepplin server in development mode

```
cd zeppelin-server
HADOOP_HOME=YOUR_HADOOP_HOME JAVA_HOME=YOUR_JAVA_HOME mvn exec:java -Dexec.mainClass="com.nflabs.zeppelin.server.ZeppelinServer" -Dexec.args=""
```

or use daemon script

```
bin/zeppelin-daemon start
```


Server will be run on http://localhost:8080

## JIRA
Zeppelin manages it's issues in Jira. [https://issues.apache.org/jira/browse/ZEPPELIN](https://issues.apache.org/jira/browse/ZEPPELIN)

## Stay involved
Everyone is welcome to join our mailling list:

 * [users@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-users/) is for usage questions, help, and announcements [ [subscribe](mailto:users-subscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20subscribe), [unsubscribe](mailto:users-unsubscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20unsubscribe), [archive](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-users/) ]
 * [dev@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-users/) is for people who want to contribute code to Zeppelin.[ [subscribe](mailto:dev-subscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20subscribe), [unsubscribe](mailto:dev-unsubscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20unsubscribe), [archive](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-dev/) ]
 * [commits@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-commits/) is for commit messages and patches to Zeppelin. [ [subscribe](mailto:commits-subscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20subscribe), [unsubscribe](mailto:commits-unsubscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20unsubscribe), [archive](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-commits/) ]